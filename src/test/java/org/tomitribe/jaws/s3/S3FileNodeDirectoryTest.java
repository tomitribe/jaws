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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.tomitribe.util.Archive;
import org.tomitribe.util.IO;
import org.tomitribe.util.Join;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.tomitribe.jaws.s3.Asserts.assertType;

public class S3FileNodeDirectoryTest {

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
                .add("repository/org.color.bright/green/1/1.4/bar.txt", "yellow")
                .add("repository/org.color.bright/green/1/1.4/a/bar.txt", "yellow")
                .add("repository/org.color.bright/green/1/1.4/a/b/c/foo.txt", "magenta")
                .add("repository/org.color.bright/green/1/1.4/a/b/c/d/e/f/bar.txt", "cyan")
                .add("repository/junit/junit/4/4.12/bar.txt", "blue")
                .add("repository/io.tomitribe/crest/5/5.4.1.2/baz.txt", "orange")
                .toDir(store);

        final S3Bucket bucket = s3Client.getBucket("repository");

        file = bucket.getFile("org.color.bright/green/1/1.4/foo.txt").getParentFile();

        // Check to ensure our current node type is `Directory`
        assertType(file, "Directory");
    }

    @Test
    public void exists() throws IOException {
        assertTrue(file.exists());

        // type should still be Directory after the above call
        assertType(file, "Directory");
    }

    @Test
    public void isFile() {
        assertFalse(file.isFile());

        // type should still be Directory after the above call
        assertType(file, "Directory");
    }

    @Test
    public void isDirectory() {
        assertTrue(file.isDirectory());

        // type should still be Directory after the above call
        assertType(file, "Directory");
    }

    @Test
    public void getParentFile() {
        final S3File parent = file.getParentFile();
        assertNotNull(parent);
        assertEquals("1", parent.getName());
        assertEquals("org.color.bright/green/1", parent.getAbsoluteName());
    }

    @Test
    public void getFile() {
        final S3File child = this.file.getFile("allowed");
        assertEquals("org.color.bright/green/1/1.4/allowed", child.getAbsoluteName());
        // type should still be Directory after the above call
        assertType(child, "Unknown");
    }

    @Test
    public void files() {
        final List<S3File> list = file.files().collect(Collectors.toList());
        assertEquals("" +
                "org.color.bright/green/1/1.4/a/b/c/d/e/f/bar.txt\n" +
                "org.color.bright/green/1/1.4/a/b/c/foo.txt\n" +
                "org.color.bright/green/1/1.4/a/bar.txt\n" +
                "org.color.bright/green/1/1.4/bar.txt\n" +
                "org.color.bright/green/1/1.4/foo.txt", Join.join("\n", S3File::getAbsoluteName, list));
    }

    @Test
    public void testFiles() {
        final List<S3File> list = file.files(new ListObjectsRequest()).collect(Collectors.toList());
        assertEquals("" +
                "org.color.bright/green/1/1.4/a/b/c/d/e/f/bar.txt\n" +
                "org.color.bright/green/1/1.4/a/b/c/foo.txt\n" +
                "org.color.bright/green/1/1.4/a/bar.txt\n" +
                "org.color.bright/green/1/1.4/bar.txt\n" +
                "org.color.bright/green/1/1.4/foo.txt", Join.join("\n", S3File::getAbsoluteName, list));
    }

    @Test
    public void walk() {
        {
            final List<String> list = file.walk()
                    .map(S3File::getAbsoluteName)
                    .sorted()
                    .collect(Collectors.toList());

            assertEquals("org.color.bright/green/1/1.4/a\n" +
                    "org.color.bright/green/1/1.4/a/b\n" +
                    "org.color.bright/green/1/1.4/a/b/c\n" +
                    "org.color.bright/green/1/1.4/a/b/c/d\n" +
                    "org.color.bright/green/1/1.4/a/b/c/d/e\n" +
                    "org.color.bright/green/1/1.4/a/b/c/d/e/f\n" +
                    "org.color.bright/green/1/1.4/a/b/c/d/e/f/bar.txt\n" +
                    "org.color.bright/green/1/1.4/a/b/c/foo.txt\n" +
                    "org.color.bright/green/1/1.4/a/bar.txt\n" +
                    "org.color.bright/green/1/1.4/bar.txt\n" +
                    "org.color.bright/green/1/1.4/foo.txt", Join.join("\n", list));
        }
        {
            final List<String> list = file.getParentFile().getParentFile().walk()
                    .map(S3File::getAbsoluteName)
                    .sorted()
                    .collect(Collectors.toList());

            assertEquals("org.color.bright/green/1\n" +
                    "org.color.bright/green/1/1.4\n" +
                    "org.color.bright/green/1/1.4/a\n" +
                    "org.color.bright/green/1/1.4/a/b\n" +
                    "org.color.bright/green/1/1.4/a/b/c\n" +
                    "org.color.bright/green/1/1.4/a/b/c/d\n" +
                    "org.color.bright/green/1/1.4/a/b/c/d/e\n" +
                    "org.color.bright/green/1/1.4/a/b/c/d/e/f\n" +
                    "org.color.bright/green/1/1.4/a/b/c/d/e/f/bar.txt\n" +
                    "org.color.bright/green/1/1.4/a/b/c/foo.txt\n" +
                    "org.color.bright/green/1/1.4/a/bar.txt\n" +
                    "org.color.bright/green/1/1.4/bar.txt\n" +
                    "org.color.bright/green/1/1.4/foo.txt", Join.join("\n", list));
        }
    }

    @Test
    public void walk3() {
        {
            final List<String> list = file.walk(3)
                    .map(S3File::getAbsoluteName)
                    .sorted()
                    .collect(Collectors.toList());

            assertEquals("" +
                    "org.color.bright/green/1/1.4/a\n" +
                    "org.color.bright/green/1/1.4/a/b\n" +
                    "org.color.bright/green/1/1.4/a/b/c\n" +
                    "org.color.bright/green/1/1.4/a/bar.txt\n" +
                    "org.color.bright/green/1/1.4/bar.txt\n" +
                    "org.color.bright/green/1/1.4/foo.txt", Join.join("\n", list));
        }
        {
            final List<String> list = file.getParentFile().getParentFile().walk(3)
                    .map(S3File::getAbsoluteName)
                    .sorted()
                    .collect(Collectors.toList());

            assertEquals("" +
                    "org.color.bright/green/1\n" +
                    "org.color.bright/green/1/1.4\n" +
                    "org.color.bright/green/1/1.4/a\n" +
                    "org.color.bright/green/1/1.4/bar.txt\n" +
                    "org.color.bright/green/1/1.4/foo.txt", Join.join("\n", list));
        }
    }

    @Test
    public void getAbsoluteName() {
        assertEquals("org.color.bright/green/1/1.4", file.getAbsoluteName());
    }

    @Test
    public void getName() {
        assertEquals("1.4", file.getName());
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

        final S3File file = bucket.getFile("junit/junit/4/4.12/bar.txt").getParentFile();
        assertType(file, "Directory");
        assertTrue(file.exists());

        try {
            file.delete();
            fail("UnsupportedOperationException");
        } catch (final UnsupportedOperationException e) {
        }


        assertTrue(bucket.getFile("junit/junit/4/4.12/bar.txt").exists());
    }

    @Test
    public void getValueAsStream() throws IOException {
        assertUnsupported(() -> file.getValueAsStream());
    }

    @Test
    public void getValueAsString() {
        assertUnsupported(() -> file.getValueAsString());
    }

    @Test
    public void setValueAsStream() {
        assertUnsupported(() -> file.setValueAsStream(IO.read("foo")));
    }

    @Test
    public void testSetValueAsStream() throws IOException {
        assertUnsupported(() -> file.setValueAsStream());
    }

    @Test
    public void setValueAsFile() throws IOException {
        assertUnsupported(() -> file.setValueAsFile(new File("foo")));
    }

    @Test
    public void setValueAsString() {
        assertUnsupported(() -> file.setValueAsString("foo"));
    }

    @Test
    public void getBucketName() {
        assertEquals("repository", file.getBucketName());
    }

    @Test
    public void getETag() {
        assertUnsupported(() -> file.getETag());
    }

    @Test
    public void getSize() {
        assertUnsupported(() -> file.getSize());
    }

    @Test
    public void getLastModified() {
        assertUnsupported(() -> file.getLastModified());
    }

    private void assertUnsupported(final Runnable action) {
        try {
            action.run();
            fail("NoSuchS3ObjectException should have been thrown");
        } catch (final UnsupportedOperationException e) {
            // pass
        }
    }
}
