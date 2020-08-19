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

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.tomitribe.util.Archive;
import org.tomitribe.util.IO;
import org.tomitribe.util.Join;
import org.tomitribe.util.dir.Dir;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class S3BucketTest {

    @Rule
    public MockS3 mockS3 = new MockS3();
    private File store;
    private S3Client s3Client;


    @Before
    public final void setUp() throws Exception {
        this.store = mockS3.getBlobStoreLocation();
        this.s3Client = new S3Client(mockS3.getAmazonS3());
    }

    @Test
    public void objects() throws IOException {
        new Archive()
                .add("repository/org.color/red/1/1.4/foo.txt", "")
                .add("repository/org.color.bright/green/1/1.4/foo.txt", "")
                .add("repository/junit/junit/4/4.12/bar.txt", "")
                .add("repository/io.tomitribe/crest/5/5.4.1.2/baz.txt", "")
                .toDir(store);

        final S3Bucket bucket = s3Client.getBucket("repository");
        final List<String> list = bucket.objects()
                .map(S3Entry::getKey)
                .sorted()
                .collect(Collectors.toList());

        assertEquals("io.tomitribe/crest/5/5.4.1.2/baz.txt\n" +
                "junit/junit/4/4.12/bar.txt\n" +
                "org.color.bright/green/1/1.4/foo.txt\n" +
                "org.color/red/1/1.4/foo.txt", Join.join("\n", list));
    }

    @Test
    public void putObjectString() throws IOException {
        final S3Bucket bucket = s3Client.createBucket("repository");
        bucket.putObject("org.color/red/1/1.4/foo.txt", "red");
        bucket.putObject("org.color.bright/green/1/1.4/foo.txt", "green");
        bucket.putObject("junit/junit/4/4.12/bar.txt", "blue");
        bucket.putObject("io.tomitribe/crest/5/5.4.1.2/baz.txt", "orange");

        final List<String> paths = paths(store);

        assertEquals("/repository/io.tomitribe/crest/5/5.4.1.2/baz.txt\n" +
                "/repository/junit/junit/4/4.12/bar.txt\n" +
                "/repository/org.color.bright/green/1/1.4/foo.txt\n" +
                "/repository/org.color/red/1/1.4/foo.txt", Join.join("\n", paths));

        assertContent(store, "red", "repository/org.color/red/1/1.4/foo.txt");
        assertContent(store, "green", "repository/org.color.bright/green/1/1.4/foo.txt");
        assertContent(store, "blue", "repository/junit/junit/4/4.12/bar.txt");
        assertContent(store, "orange", "repository/io.tomitribe/crest/5/5.4.1.2/baz.txt");
    }

    private static void assertContent(final File store, final String orange, final String s) throws IOException {
        assertEquals(orange, IO.slurp(new File(store, s)));
    }

    @Test
    public void putObjectFile() throws IOException {
        final File data = new Archive()
                .add("red.txt", "red")
                .add("green.txt", "green")
                .add("blue.txt", "blue")
                .add("orange.txt", "orange")
                .toDir();

        final S3Bucket bucket = s3Client.createBucket("repository");
        bucket.putObject("org.color/red/1/1.4/foo.txt", new File(data, "red.txt"));
        bucket.putObject("org.color.bright/green/1/1.4/foo.txt", new File(data, "green.txt"));
        bucket.putObject("junit/junit/4/4.12/bar.txt", new File(data, "blue.txt"));
        bucket.putObject("io.tomitribe/crest/5/5.4.1.2/baz.txt", new File(data, "orange.txt"));

        final List<String> paths = paths(store);

        assertEquals("/repository/io.tomitribe/crest/5/5.4.1.2/baz.txt\n" +
                "/repository/junit/junit/4/4.12/bar.txt\n" +
                "/repository/org.color.bright/green/1/1.4/foo.txt\n" +
                "/repository/org.color/red/1/1.4/foo.txt", Join.join("\n", paths));

        assertContent(store, "red", "repository/org.color/red/1/1.4/foo.txt");
        assertContent(store, "green", "repository/org.color.bright/green/1/1.4/foo.txt");
        assertContent(store, "blue", "repository/junit/junit/4/4.12/bar.txt");
        assertContent(store, "orange", "repository/io.tomitribe/crest/5/5.4.1.2/baz.txt");
    }

    @Test
    public void putObjectStream() throws IOException {
        final File data = new Archive()
                .add("red.txt", "red")
                .add("green.txt", "green")
                .add("blue.txt", "blue")
                .add("orange.txt", "orange")
                .toDir();

        final S3Bucket bucket = s3Client.createBucket("repository");
        bucket.putObject("org.color/red/1/1.4/foo.txt", IO.read(new File(data, "red.txt")), new ObjectMetadata());
        bucket.putObject("org.color.bright/green/1/1.4/foo.txt", IO.read(new File(data, "green.txt")), new ObjectMetadata());
        bucket.putObject("junit/junit/4/4.12/bar.txt", IO.read(new File(data, "blue.txt")), new ObjectMetadata());
        bucket.putObject("io.tomitribe/crest/5/5.4.1.2/baz.txt", IO.read(new File(data, "orange.txt")), new ObjectMetadata());

        final List<String> paths = paths(store);

        assertEquals("/repository/io.tomitribe/crest/5/5.4.1.2/baz.txt\n" +
                "/repository/junit/junit/4/4.12/bar.txt\n" +
                "/repository/org.color.bright/green/1/1.4/foo.txt\n" +
                "/repository/org.color/red/1/1.4/foo.txt", Join.join("\n", paths));

        assertContent(store, "red", "repository/org.color/red/1/1.4/foo.txt");
        assertContent(store, "green", "repository/org.color.bright/green/1/1.4/foo.txt");
        assertContent(store, "blue", "repository/junit/junit/4/4.12/bar.txt");
        assertContent(store, "orange", "repository/io.tomitribe/crest/5/5.4.1.2/baz.txt");

    }

    @Test
    public void getObject() throws IOException {
        final S3Bucket bucket = s3Client.createBucket("repository");
        bucket.putObject("org.color/red/1/1.4/foo.txt", "red");
        bucket.putObject("org.color.bright/green/1/1.4/foo.txt", "green");
        bucket.putObject("junit/junit/4/4.12/bar.txt", "blue");
        bucket.putObject("io.tomitribe/crest/5/5.4.1.2/baz.txt", "orange");

        final List<String> paths = paths(store);

        assertEquals("/repository/io.tomitribe/crest/5/5.4.1.2/baz.txt\n" +
                "/repository/junit/junit/4/4.12/bar.txt\n" +
                "/repository/org.color.bright/green/1/1.4/foo.txt\n" +
                "/repository/org.color/red/1/1.4/foo.txt", Join.join("\n", paths));

        assertObject(bucket, "repository", "org.color/red/1/1.4/foo.txt", "red");
        assertObject(bucket, "repository", "org.color.bright/green/1/1.4/foo.txt", "green");
        assertObject(bucket, "repository", "junit/junit/4/4.12/bar.txt", "blue");
        assertObject(bucket, "repository", "io.tomitribe/crest/5/5.4.1.2/baz.txt", "orange");
    }

    public static void assertObject(final S3Bucket bucket, final String bucketName, final String path, final String red) throws IOException {
        final S3Object object = bucket.getObject(path);
        assertEquals(bucketName, object.getBucketName());
        assertEquals(path, object.getKey());
        assertEquals(red, IO.slurp(object.getObjectContent()));
    }

    @Test
    public void getObjectAsStream() throws IOException {
        final S3Bucket bucket = s3Client.createBucket("repository");
        bucket.putObject("org.color/red/1/1.4/foo.txt", "red");
        bucket.putObject("org.color.bright/green/1/1.4/foo.txt", "green");
        bucket.putObject("junit/junit/4/4.12/bar.txt", "blue");
        bucket.putObject("io.tomitribe/crest/5/5.4.1.2/baz.txt", "orange");

        final List<String> paths = paths(store);

        assertEquals("/repository/io.tomitribe/crest/5/5.4.1.2/baz.txt\n" +
                "/repository/junit/junit/4/4.12/bar.txt\n" +
                "/repository/org.color.bright/green/1/1.4/foo.txt\n" +
                "/repository/org.color/red/1/1.4/foo.txt", Join.join("\n", paths));

        assertEquals("red", IO.slurp(bucket.getObjectAsStream("org.color/red/1/1.4/foo.txt")));
        assertEquals("green", IO.slurp(bucket.getObjectAsStream("org.color.bright/green/1/1.4/foo.txt")));
        assertEquals("blue", IO.slurp(bucket.getObjectAsStream("junit/junit/4/4.12/bar.txt")));
        assertEquals("orange", IO.slurp(bucket.getObjectAsStream("io.tomitribe/crest/5/5.4.1.2/baz.txt")));

        try {
            bucket.getObjectAsStream("does/not/exist.txt");
            fail("NoSuchS3ObjectException should have been thrown");
        } catch (final NoSuchS3ObjectException e) {
            assertEquals("Key 'does/not/exist.txt' not found in bucket 'repository'", e.getMessage());
            assertEquals("repository", e.getBucketName());
            assertEquals("does/not/exist.txt", e.getKey());
        }
    }

    @Test
    public void getObjectAsString() {
        final S3Bucket bucket = s3Client.createBucket("repository");
        bucket.putObject("org.color/red/1/1.4/foo.txt", "red");
        bucket.putObject("org.color.bright/green/1/1.4/foo.txt", "green");
        bucket.putObject("junit/junit/4/4.12/bar.txt", "blue");
        bucket.putObject("io.tomitribe/crest/5/5.4.1.2/baz.txt", "orange");

        final List<String> paths = paths(store);

        assertEquals("/repository/io.tomitribe/crest/5/5.4.1.2/baz.txt\n" +
                "/repository/junit/junit/4/4.12/bar.txt\n" +
                "/repository/org.color.bright/green/1/1.4/foo.txt\n" +
                "/repository/org.color/red/1/1.4/foo.txt", Join.join("\n", paths));

        assertEquals("red", bucket.getObjectAsString("org.color/red/1/1.4/foo.txt"));
        assertEquals("green", bucket.getObjectAsString("org.color.bright/green/1/1.4/foo.txt"));
        assertEquals("blue", bucket.getObjectAsString("junit/junit/4/4.12/bar.txt"));
        assertEquals("orange", bucket.getObjectAsString("io.tomitribe/crest/5/5.4.1.2/baz.txt"));

        try {
            bucket.getObjectAsStream("does/not/exist.txt");
            fail("NoSuchS3ObjectException should have been thrown");
        } catch (final NoSuchS3ObjectException e) {
            assertEquals("Key 'does/not/exist.txt' not found in bucket 'repository'", e.getMessage());
            assertEquals("repository", e.getBucketName());
            assertEquals("does/not/exist.txt", e.getKey());
        }
    }

    @Test
    public void getName() {
        final S3Bucket bucket = s3Client.createBucket("repository");
        assertEquals("repository", bucket.getName());
    }


    public static List<String> paths(final File store) {
        final Dir dir = Dir.of(Dir.class, store);
        return paths(dir.get(), dir.files().collect(Collectors.toList()));
    }

    public static String path(final File file) {
        if (file.isDirectory()) return file.getAbsolutePath() + "/";
        else return file.getAbsolutePath();
    }

    public static List<String> paths(final File dir, final List<File> list) {
        final int trim = path(dir).length() - 1;
        return list.stream()
                .map(S3BucketTest::path)
                .map(s -> s.substring(trim))
                .sorted()
                .collect(Collectors.toList());
    }

}
