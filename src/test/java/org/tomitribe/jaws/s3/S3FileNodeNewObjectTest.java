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

import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.transfer.Upload;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.tomitribe.util.Archive;
import org.tomitribe.util.IO;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.tomitribe.jaws.s3.Asserts.assertType;

/**
 * Similar to S3FileNodeUnknownTest but the S3File in an unknown
 * state doesn't happen to point to a file that exists.
 */
public class S3FileNodeNewObjectTest {

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
        file = bucket.asFile().getFile("org.color.bright/green/does/not/exist.txt");

        assertFalse(file.exists());

        // Check to ensure our current node type is `Object`
        assertType(file, "NewObject");
    }

    @Test
    public void exists() throws IOException {
        assertFalse(file.exists());

        // type should be Object after the above call
        assertType(file, "NewObject");
    }

    @Test
    public void isFile() {
        assertFalse(file.isFile());

        // type should be Object after the above call
        assertType(file, "NewObject");
    }

    @Test
    public void isDirectory() {
        assertFalse(file.isDirectory());

        // type should be Object after the above call
        assertType(file, "NewObject");
    }

    @Test
    public void getParentFile() {
        final S3File parent = file.getParentFile();
        assertNotNull(parent);
        assertEquals("not", parent.getName());
        assertEquals("org.color.bright/green/does/not", parent.getAbsoluteName());
    }

    @Test
    public void getFile() {
        final S3File child = this.file.getFile("allowed");
        assertEquals("org.color.bright/green/does/not/exist.txt/allowed", child.getAbsoluteName());
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
    public void walk() {
        final List<String> list = file.walk()
                .map(S3File::getAbsoluteName)
                .sorted()
                .collect(Collectors.toList());

        assertEquals(0, list.size());
    }

    @Test
    public void getAbsoluteName() {
        assertEquals("org.color.bright/green/does/not/exist.txt", file.getAbsoluteName());
    }

    @Test
    public void getName() {
        assertEquals("exist.txt", file.getName());
    }

    @Test
    public void getBucket() {
        final S3Bucket bucket = file.getBucket();
        assertNotNull(bucket);
        assertEquals("repository", bucket.getName());
    }

    @Test
    public void delete() {
        final S3Bucket bucket = file.getBucket();

        final S3File file = bucket.asFile().getFile("org.color.bright/blue/does/not/exist.txt");
        assertFalse(file.exists());

        // Check to ensure our current node type
        assertType(file, "NewObject");

        assertFalse(file.exists());

        try {
            file.delete();
            fail("NoSuchS3ObjectException");
        } catch (final NoSuchS3ObjectException e) {
        }

        assertFalse(file.exists());
        assertType(file, "NewObject");

        // The object should be deleted
        try {
            bucket.getFile("org.color.bright/blue/does/not/exist.txt");
            fail("Expected AmazonS3Exception");
        } catch (final AmazonS3Exception e) {
            assertTrue(e.getMessage().contains("Not Found"));
        }
    }

    @Test
    public void getValueAsStream() throws IOException {
        assertNoSuchObject(() -> file.getValueAsStream());
    }

    @Test
    public void getValueAsString() {
        assertNoSuchObject(() -> file.getValueAsString());
    }

    @Test
    public void setValueAsStream() {
        // State before the update
        assertType(file, "NewObject");

        file.setValueAsStream(IO.read("forrest"));

        // type should be UpdatedObject after the above call
        assertType(file, "UpdatedObject");

        // State after the update
        assertEquals("c09321dbfe6dd09c81a36b9a31384dd3", file.getETag());
        assertEquals(0, file.getSize()); // TODO Either a bug in the Amazon S3 API or the Mock S3 implementation
        assertEquals("forrest", file.getValueAsString());
    }

    @Test
    public void upload() throws Exception {
        // State before the update
        assertType(file, "NewObject");

        final String value = "forrest";
        file.upload(IO.read(value), value.length()).waitForUploadResult();

        // type should be Object after the above call
        assertType(file, "Metadata");
// State after the update
        assertEquals("c09321dbfe6dd09c81a36b9a31384dd3", file.getETag());
        assertEquals(7, file.getSize());
        assertEquals("forrest", file.getValueAsString());
    }

    @Test
    public void setValueAsFile() throws IOException {
        // State before the update
        assertType(file, "NewObject");

        final File tempFile = File.createTempFile("foo", "bar");
        tempFile.deleteOnExit();
        IO.copy(IO.read("forrest"), tempFile);

        file.setValueAsFile(tempFile);

        // type should be UpdatedObject after the above call
        assertType(file, "UpdatedObject");

        // State after the update
        assertEquals("c09321dbfe6dd09c81a36b9a31384dd3", file.getETag());
        assertEquals(0, file.getSize()); // TODO Either a bug in the Amazon S3 API or the Mock S3 implementation
        assertEquals("forrest", file.getValueAsString());

    }

    @Test
    public void setValueAsString() {
        // State before the update
        assertType(file, "NewObject");

        file.setValueAsString("forrest");

        // type should be Object after the above call
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
        assertNoSuchObject(() -> file.getETag());
    }

    @Test
    public void getSize() {
        assertNoSuchObject(() -> file.getSize());
    }

    @Test
    public void getLastModified() {
        assertNoSuchObject(() -> file.getLastModified());
    }

    private void assertNoSuchObject(final Runnable action) {
        try {
            action.run();
            fail("NoSuchS3ObjectException should have been thrown");
        } catch (final NoSuchS3ObjectException e) {
            assertEquals("Key 'org.color.bright/green/does/not/exist.txt' not found in bucket 'repository'", e.getMessage());
        }
    }

}
