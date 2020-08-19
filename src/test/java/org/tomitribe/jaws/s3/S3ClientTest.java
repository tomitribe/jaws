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
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class S3ClientTest {

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
    public void getBucket() throws IOException {
        new Archive()
                .add("red/org.color/red/1/1.4/foo.txt", "")
                .add("green/org.color.bright/green/1/1.4/foo.txt", "")
                .add("blue/junit/junit/4/4.12/bar.txt", "")
                .add("blue/io.tomitribe/crest/5/5.4.1.2/baz.txt", "")
                .toDir(store);

        final S3Bucket bucket = s3Client.getBucket("red");
        assertNotNull(bucket);
        assertEquals("red", bucket.getName());
    }

    @Test
    public void buckets() throws IOException {
        new Archive()
                .add("red/org.color/red/1/1.4/foo.txt", "")
                .add("green/org.color.bright/green/1/1.4/foo.txt", "")
                .add("blue/junit/junit/4/4.12/bar.txt", "")
                .add("blue/io.tomitribe/crest/5/5.4.1.2/baz.txt", "")
                .toDir(store);

        final List<String> list = s3Client.buckets()
                .map(S3Bucket::getName)
                .sorted()
                .collect(Collectors.toList());

        assertEquals("" +
                "blue\n" +
                "green\n" +
                "red", Join.join("\n", list));
    }

    @Test
    public void getS3() {
        assertNotNull(s3Client.getS3());
    }
}
