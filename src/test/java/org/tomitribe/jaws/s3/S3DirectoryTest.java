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
import static org.junit.Assert.assertNotNull;
import static org.tomitribe.jaws.s3.S3BucketTest.assertContent;

public class S3DirectoryTest {

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
                .add("repository/org.color/red/1/1.3/foo.txt", "")
                .add("repository/org.color/red/1/1.2/foo.txt", "")
                .add("repository/org.color/red/1/1.1/foo.txt", "")
                .add("repository/org.color/green/2/2.3/foo.txt", "")
                .add("repository/org.color.bright/green/1/1.4/foo.txt", "")
                .add("repository/junit/junit/4/4.12/bar.txt", "")
                .add("repository/io.tomitribe/crest/5/5.4.1.2/baz.txt", "")
                .toDir(store);

        final S3File repository = s3Client.getBucket("repository").asFile();

        {
            final List<String> list = repository.files()
                    .map(S3File::getAbsoluteName)
                    .sorted()
                    .collect(Collectors.toList());

            assertEquals("" +
                            "io.tomitribe/crest/5/5.4.1.2/baz.txt\n" +
                            "junit/junit/4/4.12/bar.txt\n" +
                            "org.color.bright/green/1/1.4/foo.txt\n" +
                            "org.color/green/2/2.3/foo.txt\n" +
                            "org.color/red/1/1.1/foo.txt\n" +
                            "org.color/red/1/1.2/foo.txt\n" +
                            "org.color/red/1/1.3/foo.txt\n" +
                            "org.color/red/1/1.4/foo.txt"
                    , Join.join("\n", list));
        }

        final S3File orgColor = repository.getFile("org.color/");

        {
            final List<String> list = orgColor.files()
                    .map(S3File::getAbsoluteName)
                    .sorted()
                    .collect(Collectors.toList());

            assertEquals("" +
                            "org.color/green/2/2.3/foo.txt\n" +
                            "org.color/red/1/1.1/foo.txt\n" +
                            "org.color/red/1/1.2/foo.txt\n" +
                            "org.color/red/1/1.3/foo.txt\n" +
                            "org.color/red/1/1.4/foo.txt"
                    , Join.join("\n", list));
        }

        final S3File red = orgColor.getFile("red/");

        {
            final List<String> list = red.files()
                    .map(S3File::getAbsoluteName)
                    .sorted()
                    .collect(Collectors.toList());

            assertEquals("" +
                            "org.color/red/1/1.1/foo.txt\n" +
                            "org.color/red/1/1.2/foo.txt\n" +
                            "org.color/red/1/1.3/foo.txt\n" +
                            "org.color/red/1/1.4/foo.txt"
                    , Join.join("\n", list));
        }
    }

    @Test
    public void putObjectString() throws IOException {
        final S3Bucket bucket = s3Client.createBucket("repository");

        bucket.asFile()
                .getFile("org.color/")
                .getFile("red/1/1.4/foo.txt")
                .setValueAsString("red");

        bucket.asFile()
                .getFile("org.color.bright/")
                .getFile("green/")
                .getFile("1/1.4/foo.txt").setValueAsString("green");

        assertContent(store, "red", "repository/org.color/red/1/1.4/foo.txt");
        assertContent(store, "green", "repository/org.color.bright/green/1/1.4/foo.txt");
    }

    @Test
    public void putObjectFile() throws IOException {
        final File data = new Archive()
                .add("red.txt", "red")
                .add("green.txt", "green")
                .toDir();

        final S3Bucket bucket = s3Client.createBucket("repository");

        bucket.asFile()
                .getFile("org.color/")
                .getFile("red/1/1.4/foo.txt")
                .setValueAsFile(new File(data, "red.txt"));

        bucket.asFile()
                .getFile("org.color.bright/")
                .getFile("green/")
                .getFile("1/1.4/foo.txt")
                .setValueAsFile(new File(data, "green.txt"));

        assertContent(store, "red", "repository/org.color/red/1/1.4/foo.txt");
        assertContent(store, "green", "repository/org.color.bright/green/1/1.4/foo.txt");
    }

    @Test
    public void putObjectStream() throws IOException {
        final File data = new Archive()
                .add("red.txt", "red")
                .add("green.txt", "green")
                .toDir();

        final S3Bucket bucket = s3Client.createBucket("repository");

        bucket.asFile()
                .getFile("org.color/")
                .getFile("red/1/1.4/foo.txt")
                .setValueAsStream(IO.read(new File(data, "red.txt")));

        bucket.asFile()
                .getFile("org.color.bright/")
                .getFile("green/")
                .getFile("1/1.4/foo.txt").setValueAsStream(IO.read(new File(data, "green.txt")));

        assertContent(store, "red", "repository/org.color/red/1/1.4/foo.txt");
        assertContent(store, "green", "repository/org.color.bright/green/1/1.4/foo.txt");
    }

    @Test
    public void getObjectAsStream() throws IOException {
        final S3Bucket bucket = s3Client.createBucket("repository");
        bucket.putObject("org.color/red/1/1.4/foo.txt", "red");
        bucket.putObject("org.color.bright/green/1/1.4/foo.txt", "green");
        bucket.putObject("junit/junit/4/4.12/bar.txt", "blue");
        bucket.putObject("io.tomitribe/crest/5/5.4.1.2/baz.txt", "orange");

        final S3File directory = bucket.asFile().getFile("org.color/");

        assertEquals("red", IO.slurp(directory.getFile("red/1/1.4/foo.txt").getValueAsStream()));
    }

    @Test
    public void getObjectAsString() {
        final S3Bucket bucket = s3Client.createBucket("repository");
        bucket.putObject("org.color/red/1/1.4/foo.txt", "red");
        bucket.putObject("org.color.bright/green/1/1.4/foo.txt", "green");
        bucket.putObject("junit/junit/4/4.12/bar.txt", "blue");
        bucket.putObject("io.tomitribe/crest/5/5.4.1.2/baz.txt", "orange");

        final S3File directory = bucket.asFile().getFile("org.color/");

        assertEquals("red", directory.getFile("red/1/1.4/foo.txt").getValueAsString());
    }

    @Test
    public void getDirectory() throws IOException {
        new Archive()
                .add("repository/org.color/red/1/1.4/foo.txt", "")
                .add("repository/org.color/red/1/1.3/foo.txt", "")
                .add("repository/org.color/red/1/1.2/foo.txt", "")
                .add("repository/org.color/red/1/1.1/foo.txt", "")
                .add("repository/org.color/green/2/2.3/foo.txt", "")
                .add("repository/org.color.bright/green/1/1.4/foo.txt", "")
                .add("repository/junit/junit/4/4.12/bar.txt", "")
                .add("repository/io.tomitribe/crest/5/5.4.1.2/baz.txt", "")
                .toDir(store);

        final S3File repository = s3Client.getBucket("repository").asFile();

        final S3File directory = repository.getFile("junit/");
        assertNotNull(directory);
        assertEquals("junit", directory.getName());
    }

    @Test
    public void getParentDirectory() throws IOException {
        new Archive()
                .add("repository/org.color/red/1/1.4/foo.txt", "")
                .add("repository/org.color/red/1/1.3/foo.txt", "")
                .add("repository/org.color/red/1/1.2/foo.txt", "")
                .add("repository/org.color/red/1/1.1/foo.txt", "")
                .add("repository/org.color/green/2/2.3/foo.txt", "")
                .add("repository/org.color.bright/green/1/1.4/foo.txt", "")
                .add("repository/junit/junit/4/4.12/bar.txt", "")
                .add("repository/io.tomitribe/crest/5/5.4.1.2/baz.txt", "")
                .toDir(store);

        final S3File directory = s3Client.getBucket("repository").asFile()
                .getFile("io.tomitribe/")
                .getFile("crest/");

        assertNotNull(directory);
        assertEquals("crest", directory.getName());

        assertNotNull(directory.getParentFile());
        assertEquals("io.tomitribe", directory.getParentFile().getName());
    }

    @Test
    public void getName() {
        final S3File directory = s3Client.createBucket("repository").asFile()
                .getFile("org.colors/")
                .getFile("green/");

        assertEquals("green", directory.getName());
    }

    @Test
    public void getAbsoluteName() {
        final S3File directory = s3Client.createBucket("repository").asFile()
                .getFile("org.colors/")
                .getFile("green/");

        assertEquals("org.colors/green", directory.getAbsoluteName());
    }


}
