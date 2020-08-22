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

import java.io.File;

import static org.junit.Assert.*;
import static org.tomitribe.jaws.s3.Asserts.assertType;

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
                .toDir(store);

        final S3Bucket bucket = s3Client.getBucket("repository");
        file = bucket.asFile().getFile("org.color.bright/green/1/1.4/foo.txt");
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
