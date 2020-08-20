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
import org.tomitribe.util.IO;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class S3FileNodeObjectSummary2Test {

    @Rule
    public MockS3 mockS3 = new MockS3();
    private S3Client s3Client;


    @Before
    public final void setUp() throws Exception {
        this.s3Client = new S3Client(mockS3.getAmazonS3());
    }

    @Test
    public void getValue() throws IOException {
        final S3Bucket bucket = s3Client.createBucket("repository");
        bucket.putObject("org.color/red/1/1.4/foo.txt", "red");
        bucket.putObject("org.color.bright/green/1/1.4/foo.txt", "green");
        bucket.putObject("junit/junit/4/4.12/bar.txt", "blue");
        bucket.putObject("io.tomitribe/crest/5/5.4.1.2/baz.txt", "orange");

        final Map<String, S3File> entries = bucket.objects().collect(Collectors.toMap(S3File::getAbsoluteName, Function.identity()));

        assertEntry(entries, "org.color/red/1/1.4/foo.txt", "red");
        assertEntry(entries, "org.color.bright/green/1/1.4/foo.txt", "green");
        assertEntry(entries, "junit/junit/4/4.12/bar.txt", "blue");
        assertEntry(entries, "io.tomitribe/crest/5/5.4.1.2/baz.txt", "orange");
    }

    @Test
    public void getValueAsStream() throws IOException {
        final S3Bucket bucket = s3Client.createBucket("repository");
        bucket.putObject("org.color/red/1/1.4/foo.txt", "red");
        bucket.putObject("org.color.bright/green/1/1.4/foo.txt", "green");
        bucket.putObject("junit/junit/4/4.12/bar.txt", "blue");
        bucket.putObject("io.tomitribe/crest/5/5.4.1.2/baz.txt", "orange");

        final Map<String, S3File> entries = bucket.objects().collect(Collectors.toMap(S3File::getAbsoluteName, Function.identity()));

        assertEntryAsStream(entries, "org.color/red/1/1.4/foo.txt", "red");
        assertEntryAsStream(entries, "org.color.bright/green/1/1.4/foo.txt", "green");
        assertEntryAsStream(entries, "junit/junit/4/4.12/bar.txt", "blue");
        assertEntryAsStream(entries, "io.tomitribe/crest/5/5.4.1.2/baz.txt", "orange");
    }

    @Test
    public void getValueAsString() throws IOException {
        final S3Bucket bucket = s3Client.createBucket("repository");
        bucket.putObject("org.color/red/1/1.4/foo.txt", "red");
        bucket.putObject("org.color.bright/green/1/1.4/foo.txt", "green");
        bucket.putObject("junit/junit/4/4.12/bar.txt", "blue");
        bucket.putObject("io.tomitribe/crest/5/5.4.1.2/baz.txt", "orange");

        final Map<String, S3File> entries = bucket.objects().collect(Collectors.toMap(S3File::getAbsoluteName, Function.identity()));

        assertEntryAsString(entries, "org.color/red/1/1.4/foo.txt", "red");
        assertEntryAsString(entries, "org.color.bright/green/1/1.4/foo.txt", "green");
        assertEntryAsString(entries, "junit/junit/4/4.12/bar.txt", "blue");
        assertEntryAsString(entries, "io.tomitribe/crest/5/5.4.1.2/baz.txt", "orange");
    }

    @Test
    public void getBucketName() {
        final S3Bucket bucket = s3Client.createBucket("repository");
        bucket.putObject("org.color/red/1/1.4/foo.txt", "red");

        final Stream<S3File> objects = bucket.objects();
        final S3File entry = objects.findAny().orElseThrow(AssertionError::new);

        assertEquals("repository", entry.getBucketName());
    }

    @Test
    public void getKey() {
        final S3Bucket bucket = s3Client.createBucket("repository");
        bucket.putObject("org.color/red/1/1.4/foo.txt", "red");

        final Stream<S3File> objects = bucket.objects();
        final S3File entry = objects.findAny().orElseThrow(AssertionError::new);

        assertEquals("org.color/red/1/1.4/foo.txt", entry.getAbsoluteName());
    }

    @Test
    public void getSize() {
        final S3Bucket bucket = s3Client.createBucket("repository");
        bucket.putObject("org.color/red/1/1.4/foo.txt", "red");

        final Stream<S3File> objects = bucket.objects();
        final S3File entry = objects.findAny().orElseThrow(AssertionError::new);

        assertEquals(3, entry.getSize());
    }

    @Test
    public void getName() {
        final S3Bucket bucket = s3Client.createBucket("repository");
        bucket.putObject("org.color/red/1/1.4/foo.txt", "red");

        final Stream<S3File> objects = bucket.objects();
        final S3File entry = objects.findAny().orElseThrow(AssertionError::new);

        assertEquals("foo.txt", entry.getName());
    }

    public static void assertEntry(final Map<String, S3File> entries, final String key, final String content) throws IOException {
        final S3File file = entries.get(key);
        assertEquals(key, file.getAbsoluteName());
        assertEquals(content, IO.slurp(file.getValueAsStream()));
    }

    public static void assertEntryAsStream(final Map<String, S3File> entries, final String key, final String content) throws IOException {
        final S3File entry = entries.get(key);
        assertNotNull(entry);
        assertEquals(content, IO.slurp(entry.getValueAsStream()));
    }

    public static void assertEntryAsString(final Map<String, S3File> entries, final String key, final String content) throws IOException {
        final S3File entry = entries.get(key);
        assertNotNull(entry);
        assertEquals(content, entry.getValueAsString());
    }
}
