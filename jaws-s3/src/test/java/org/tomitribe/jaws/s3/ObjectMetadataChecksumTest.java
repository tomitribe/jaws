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
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Coverage for the five checksum digests carried by {@link ObjectMetadata}.
 *
 * These are unit tests with no S3 backend: the s3proxy mock used elsewhere
 * does not implement flexible checksums (it answers any checksum header with
 * HTTP 501), so checksum carrying and parsing can only be verified directly.
 */
public class ObjectMetadataChecksumTest {

    private static final String CRC32 = "crc32value==";
    private static final String CRC32C = "crc32cvalue==";
    private static final String CRC64NVME = "crc64value==";
    private static final String SHA1 = "sha1value==";
    private static final String SHA256 = "sha256value==";

    private static void assertAllChecksums(final ObjectMetadata md) {
        assertEquals(CRC32, md.getChecksumCRC32());
        assertEquals(CRC32C, md.getChecksumCRC32C());
        assertEquals(CRC64NVME, md.getChecksumCRC64NVME());
        assertEquals(SHA1, md.getChecksumSHA1());
        assertEquals(SHA256, md.getChecksumSHA256());
    }

    private static void assertNoChecksums(final ObjectMetadata md) {
        assertNull(md.getChecksumCRC32());
        assertNull(md.getChecksumCRC32C());
        assertNull(md.getChecksumCRC64NVME());
        assertNull(md.getChecksumSHA1());
        assertNull(md.getChecksumSHA256());
    }

    @Test
    public void builderCarriesAllFive() {
        final ObjectMetadata md = ObjectMetadata.builder()
                .checksumCRC32(CRC32)
                .checksumCRC32C(CRC32C)
                .checksumCRC64NVME(CRC64NVME)
                .checksumSHA1(SHA1)
                .checksumSHA256(SHA256)
                .build();
        assertAllChecksums(md);
    }

    @Test
    public void builderDefaultsToNull() {
        assertNoChecksums(ObjectMetadata.builder().build());
    }

    /**
     * toBuilder() copies each field by hand; this guards against a checksum
     * being silently dropped on round-trip.
     */
    @Test
    public void toBuilderPreservesAllFive() {
        final ObjectMetadata original = ObjectMetadata.builder()
                .eTag("etag")
                .contentLength(7)
                .checksumCRC32(CRC32)
                .checksumCRC32C(CRC32C)
                .checksumCRC64NVME(CRC64NVME)
                .checksumSHA1(SHA1)
                .checksumSHA256(SHA256)
                .build();

        final ObjectMetadata copy = original.toBuilder().build();

        assertEquals("etag", copy.getETag());
        assertEquals(7, copy.getContentLength());
        assertAllChecksums(copy);
    }

    @Test
    public void fromPutPopulatesChecksums() {
        final PutObjectResponse response = PutObjectResponse.builder()
                .eTag("\"etag\"")
                .checksumCRC32(CRC32)
                .checksumCRC32C(CRC32C)
                .checksumCRC64NVME(CRC64NVME)
                .checksumSHA1(SHA1)
                .checksumSHA256(SHA256)
                .build();

        final ObjectMetadata md = ObjectMetadata.fromPut(response, 7);

        assertEquals("etag", md.getETag());
        assertEquals(7, md.getContentLength());
        assertAllChecksums(md);
    }

    @Test
    public void fromHeadPopulatesChecksums() {
        final HeadObjectResponse response = HeadObjectResponse.builder()
                .eTag("\"etag\"")
                .contentLength(7L)
                .checksumCRC32(CRC32)
                .checksumCRC32C(CRC32C)
                .checksumCRC64NVME(CRC64NVME)
                .checksumSHA1(SHA1)
                .checksumSHA256(SHA256)
                .build();

        assertAllChecksums(ObjectMetadata.fromHead(response));
    }

    @Test
    public void fromGetPopulatesChecksums() {
        final GetObjectResponse response = GetObjectResponse.builder()
                .eTag("\"etag\"")
                .contentLength(7L)
                .checksumCRC32(CRC32)
                .checksumCRC32C(CRC32C)
                .checksumCRC64NVME(CRC64NVME)
                .checksumSHA1(SHA1)
                .checksumSHA256(SHA256)
                .build();

        assertAllChecksums(ObjectMetadata.fromGet(response));
    }

    /**
     * A listing entry (S3Object) carries algorithm names, not digest values,
     * so checksums are absent from fromListing().
     */
    @Test
    public void fromListingHasNoChecksums() {
        final S3Object summary = S3Object.builder()
                .eTag("\"etag\"")
                .size(7L)
                .build();
        assertNoChecksums(ObjectMetadata.fromListing(summary));
    }

    @Test
    public void fromPathHasNoChecksums() throws Exception {
        final Path path = File.createTempFile("checksum", ".txt").toPath();
        Files.write(path, "forrest".getBytes());
        path.toFile().deleteOnExit();
        assertNoChecksums(ObjectMetadata.from(path));
    }
}
