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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class S3FileCopyToTest {

    @RegisterExtension
    public MockS3Extension mockS3 = new MockS3Extension();

    @Test
    public void copyToFilePreservesContentAndLeavesTheSource() {
        final S3Bucket bucket = new S3Client(mockS3.getS3Client()).createBucket("repository")
                .put("archive-staging/foo/1.0/foo.txt", "hello world");

        final S3File source = bucket.root().getFile("archive-staging/foo/1.0/foo.txt");
        final S3File destination = bucket.root().getFile("foo/1.0/foo.txt");

        source.copyTo(destination);

        assertEquals("hello world", destination.getValueAsString());
        assertEquals(11L, destination.getSize());
        // A server-side copy leaves the source object in place.
        assertEquals("hello world", source.getValueAsString());
    }

    @Test
    public void copyToKeyReturnsTheDestinationInTheSameBucket() {
        final S3Bucket bucket = new S3Client(mockS3.getS3Client()).createBucket("repository")
                .put("archive-staging/bar.txt", "orange");

        final S3File destination = bucket.root().getFile("archive-staging/bar.txt").copyTo("delivered/bar.txt");

        assertEquals("repository", destination.getBucketName());
        assertEquals("delivered/bar.txt", destination.getAbsoluteName());
        assertEquals("orange", destination.getValueAsString());
    }

    @Test
    public void copyToMissingSourceThrowsNoSuchS3ObjectException() {
        final S3Bucket bucket = new S3Client(mockS3.getS3Client()).createBucket("repository");

        final S3File source = bucket.root().getFile("does/not/exist.txt");
        final S3File destination = bucket.root().getFile("delivered/exist.txt");

        final NoSuchS3ObjectException e = assertThrows(NoSuchS3ObjectException.class,
                () -> source.copyTo(destination));
        assertTrue(e.getMessage().contains("repository"));
        assertTrue(e.getMessage().contains("does/not/exist.txt"));
    }

    @Test
    public void copyToRefreshesAPreResolvedDestination() {
        final S3Bucket bucket = new S3Client(mockS3.getS3Client()).createBucket("repository")
                .put("archive-staging/foo/1.0/foo.txt", "hello world");

        final S3File source = bucket.root().getFile("archive-staging/foo/1.0/foo.txt");
        final S3File destination = bucket.root().getFile("foo/1.0/foo.txt");

        // Resolve the destination before the copy — its cached node becomes NewObject
        assertFalse(destination.exists());

        source.copyTo(destination);

        // The same handle must now reflect the newly written object
        assertTrue(destination.exists());
        assertEquals("hello world", destination.getValueAsString());
        assertEquals(11L, destination.getSize());
    }
}
