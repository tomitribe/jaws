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
import org.tomitribe.util.Join;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class S3FileTest {

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
                .add("repository/io.tomitribe/index.txt", "purple")
                .add("repository/index.txt", "brown")
                .toDir(store);

        final S3Bucket bucket = s3Client.getBucket("repository");
        file = bucket.asFile().getFile("org.color.bright/green/1/1.4/foo.txt");
    }

    @Test
    public void walk() {
        final List<String> list = file.getBucket().asFile().walk()
                .map(S3File::getAbsoluteName)
                .sorted()
                .collect(Collectors.toList());

        assertEquals("" +
                "index.txt\n" +
                "io.tomitribe\n" +
                "io.tomitribe/crest\n" +
                "io.tomitribe/crest/5\n" +
                "io.tomitribe/crest/5/5.4.1.2\n" +
                "io.tomitribe/crest/5/5.4.1.2/baz.txt\n" +
                "io.tomitribe/index.txt\n" +
                "junit\n" +
                "junit/junit\n" +
                "junit/junit/4\n" +
                "junit/junit/4/4.12\n" +
                "junit/junit/4/4.12/bar.txt\n" +
                "org.color\n" +
                "org.color.bright\n" +
                "org.color.bright/green\n" +
                "org.color.bright/green/1\n" +
                "org.color.bright/green/1/1.4\n" +
                "org.color.bright/green/1/1.4/foo.txt\n" +
                "org.color/green\n" +
                "org.color/green/2\n" +
                "org.color/green/2/2.3\n" +
                "org.color/green/2/2.3/foo.txt", Join.join("\n", list));
    }

    @Test
    public void walkDepth2() {
        final List<String> list = file.getBucket().asFile().walk(2)
                .map(S3File::getAbsoluteName)
                .sorted()
                .collect(Collectors.toList());

        assertEquals("" +
                "index.txt\n" +
                "io.tomitribe\n" +
                "io.tomitribe/crest\n" +
                "io.tomitribe/index.txt\n" +
                "junit\n" +
                "junit/junit\n" +
                "org.color\n" +
                "org.color.bright\n" +
                "org.color.bright/green\n" +
                "org.color/green", Join.join("\n", list));
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
        assertEquals("repository", file.getBucket().getName());
    }

    @Test
    public void getBucketName() {
        assertEquals("repository", file.getBucketName());
    }

    @Test
    public void equals() {
        final S3File repository = this.file.getBucket().asFile();
        assertEquals(file, repository.getFile("org.color.bright/green/1/1.4/foo.txt"));
        assertNotEquals(file, repository.getFile("org.color.bright/green/1/1.4/bar.txt"));
        assertNotEquals(file, "org.color.bright/green/1/1.4/bar.txt");
        assertNotEquals(file, null);
    }

}
