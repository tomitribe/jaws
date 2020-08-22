/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.tomitribe.jaws.s3;

import com.amazonaws.services.s3.model.ListObjectsRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.tomitribe.util.Archive;
import org.tomitribe.util.IO;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.tomitribe.jaws.s3.Asserts.assertType;

public class S3FileNodeObjectTest {

    @Rule
    public MockS3 mockS3 = new MockS3();
    private S3File file;


    @Before
    public final void setUp() throws Exception {
        final File store = mockS3.getBlobStoreLocation();
        final S3Client s3Client = new S3Client(mockS3.getAmazonS3());

        new Archive()
                .add("repository/org.color/green/2/2.3/foo.txt", "red")
                .add("repository/org.color.bright/green/1/1.4/foo.txt", "green")
                .add("repository/junit/junit/4/4.12/bar.txt", "blue")
                .add("repository/io.tomitribe/crest/5/5.4.1.2/baz.txt", "orange")
                .toDir(store);

        final S3Bucket bucket = s3Client.getBucket("repository");
        file = bucket.getFile("org.color.bright/green/1/1.4/foo.txt");
        // Check to ensure our current node type is `Object`
        assertType(file, "Object");
    }

    @Test
    public void exists() throws IOException {
        assertTrue(file.exists());
    }

    @Test
    public void isFile() {
        assertTrue(file.isFile());
    }

    @Test
    public void isDirectory() {
        assertFalse(file.isDirectory());
    }

    @Test
    public void getParentFile() {
        final S3File parent = file.getParentFile();
        assertNotNull(parent);
        assertEquals("1.4", parent.getName());
        assertEquals("org.color.bright/green/1/1.4", parent.getAbsoluteName());
    }

    @Test
    public void getFile() {
        try {
            file.getFile("this shouldn't work");
            fail("UnsupportedOperationException should have been thrown");
        } catch (UnsupportedOperationException e) {
            assertEquals("S3File 'org.color.bright/green/1/1.4/foo.txt' is a not directory and cannot have child 'this shouldn't work'", e.getMessage());
        }
    }

    @Test
    public void files() {
        final List<S3File> list = file.files().collect(Collectors.toList());
        assertEquals(0, list.size());
    }

    @Test
    public void testFiles() {
        final List<S3File> list = file.files(new ListObjectsRequest()).collect(Collectors.toList());
        assertEquals(0, list.size());
    }

    @Test
    public void getAbsoluteName() {
        assertEquals("org.color.bright/green/1/1.4/foo.txt", file.getAbsoluteName());
    }

    @Test
    public void getName() {
        assertEquals("foo.txt", file.getName());
    }

    @Test
    public void getBucket() {
        final S3Bucket bucket = file.getBucket();
        assertNotNull(bucket);
        assertEquals("repository", bucket.getName());
    }

    @Test
    public void getValueAsStream() throws IOException {
        final String content = IO.slurp(file.getValueAsStream());
        assertEquals("green", content);
    }

    @Test
    public void getValueAsString() {
        final String content = file.getValueAsString();
        assertEquals("green", content);
    }

    @Test
    public void setValueAsStream() {
        // State before the update
        assertType(file, "Object");
        assertEquals("green", file.getValueAsString());
        assertEquals("9f27410725ab8cc8854a2769c7a516b8", file.getETag());
        assertEquals(5, file.getSize());

        file.setValueAsStream(IO.read("forrest"));

        // State after the update
        assertType(file, "UpdatedObject");
        assertEquals("forrest", file.getValueAsString());
        assertEquals("c09321dbfe6dd09c81a36b9a31384dd3", file.getETag());
        assertEquals(7, file.getSize());
    }

    @Test
    public void testSetValueAsStream() throws IOException {
        // State before the update
        assertType(file, "Object");
        assertEquals("green", file.getValueAsString());
        assertEquals("9f27410725ab8cc8854a2769c7a516b8", file.getETag());
        assertEquals(5, file.getSize());

        try (S3OutputStream s3OutputStream = file.setValueAsStream()) {
            IO.copy(IO.read("forrest"), s3OutputStream);
        }

        // State after the update
        assertType(file, "Object");
        assertEquals("forrest", file.getValueAsString());
        assertEquals("c09321dbfe6dd09c81a36b9a31384dd3", file.getETag());
        assertEquals(7, file.getSize());
    }

    @Test
    public void setValueAsFile() throws IOException {
        // State before the update
        assertType(file, "Object");
        assertEquals("green", file.getValueAsString());
        assertEquals("9f27410725ab8cc8854a2769c7a516b8", file.getETag());
        assertEquals(5, file.getSize());

        final File tempFile = File.createTempFile("foo", "bar");
        tempFile.deleteOnExit();
        IO.copy(IO.read("forrest"), tempFile);

        file.setValueAsFile(tempFile);

        // State after the update
        assertType(file, "UpdatedObject");
        assertEquals("c09321dbfe6dd09c81a36b9a31384dd3", file.getETag());
        assertEquals(0, file.getSize()); // TODO Either a bug in the Amazon S3 API or the Mock S3 implementation
        assertEquals("forrest", file.getValueAsString());
    }

    @Test
    public void setValueAsString() {
        // State before the update
        assertType(file, "Object");
        assertEquals("green", file.getValueAsString());
        assertEquals("9f27410725ab8cc8854a2769c7a516b8", file.getETag());
        assertEquals(5, file.getSize());

        file.setValueAsString("forrest");

        // State after the update
        assertType(file, "UpdatedObject");
        assertEquals("c09321dbfe6dd09c81a36b9a31384dd3", file.getETag());
        assertEquals(0, file.getSize()); // TODO Either a bug in the Amazon S3 API or the Mock S3 implementation
        assertEquals("forrest", file.getValueAsString());
    }

    @Test
    public void getBucketName() {
        assertEquals("repository", file.getBucketName());
    }

    @Test
    public void getETag() {
        assertEquals("9f27410725ab8cc8854a2769c7a516b8", file.getETag());
    }

    @Test
    public void getSize() {
        assertEquals(5, file.getSize());
    }

    @Test
    public void getLastModified() {
        final long time = file.getLastModified().getTime();
        final long tolerance = TimeUnit.SECONDS.toMillis(30);
        assertTrue(time > System.currentTimeMillis() - tolerance);
        assertTrue(time < System.currentTimeMillis() + tolerance);
    }
}
