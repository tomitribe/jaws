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

import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ObjectMetadata {
    private final String eTag;
    private final long contentLength;
    private final Instant lastModified;
    private final String contentType;
    private final Map<String, String> userMetadata;
    private final String checksumCRC32;
    private final String checksumCRC32C;
    private final String checksumCRC64NVME;
    private final String checksumSHA1;
    private final String checksumSHA256;

    private ObjectMetadata(final String eTag, final long contentLength, final Instant lastModified, final String contentType, final Map<String, String> userMetadata,
                           final String checksumCRC32, final String checksumCRC32C, final String checksumCRC64NVME, final String checksumSHA1, final String checksumSHA256) {
        this.eTag = eTag;
        this.contentLength = contentLength;
        this.lastModified = lastModified;
        this.contentType = contentType;
        this.userMetadata = userMetadata;
        this.checksumCRC32 = checksumCRC32;
        this.checksumCRC32C = checksumCRC32C;
        this.checksumCRC64NVME = checksumCRC64NVME;
        this.checksumSHA1 = checksumSHA1;
        this.checksumSHA256 = checksumSHA256;
    }

    public String getETag() {
        return eTag;
    }

    public long getContentLength() {
        return contentLength;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public String getContentType() {
        return contentType;
    }

    public Map<String, String> getUserMetadata() {
        if (userMetadata == null) return Collections.emptyMap();
        return Collections.unmodifiableMap(userMetadata);
    }

    public String getChecksumCRC32() {
        return checksumCRC32;
    }

    public String getChecksumCRC32C() {
        return checksumCRC32C;
    }

    public String getChecksumCRC64NVME() {
        return checksumCRC64NVME;
    }

    public String getChecksumSHA1() {
        return checksumSHA1;
    }

    public String getChecksumSHA256() {
        return checksumSHA256;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .eTag(eTag)
                .contentLength(contentLength)
                .lastModified(lastModified)
                .contentType(contentType)
                .userMetadata(userMetadata != null ? new HashMap<>(userMetadata) : null)
                .checksumCRC32(checksumCRC32)
                .checksumCRC32C(checksumCRC32C)
                .checksumCRC64NVME(checksumCRC64NVME)
                .checksumSHA1(checksumSHA1)
                .checksumSHA256(checksumSHA256);
    }

    public static class Builder {
        private String eTag;
        private long contentLength;
        private Instant lastModified;
        private String contentType;
        private Map<String, String> userMetadata;
        private String checksumCRC32;
        private String checksumCRC32C;
        private String checksumCRC64NVME;
        private String checksumSHA1;
        private String checksumSHA256;

        public Builder eTag(final String eTag) {
            this.eTag = eTag;
            return this;
        }

        public Builder contentLength(final long contentLength) {
            this.contentLength = contentLength;
            return this;
        }

        public Builder lastModified(final Instant lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        public Builder contentType(final String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder userMetadata(final Map<String, String> userMetadata) {
            this.userMetadata = userMetadata;
            return this;
        }

        public Builder userMetadata(final String key, final String value) {
            if (this.userMetadata == null) {
                this.userMetadata = new HashMap<>();
            }
            this.userMetadata.put(key, value);
            return this;
        }

        public Builder checksumCRC32(final String checksumCRC32) {
            this.checksumCRC32 = checksumCRC32;
            return this;
        }

        public Builder checksumCRC32C(final String checksumCRC32C) {
            this.checksumCRC32C = checksumCRC32C;
            return this;
        }

        public Builder checksumCRC64NVME(final String checksumCRC64NVME) {
            this.checksumCRC64NVME = checksumCRC64NVME;
            return this;
        }

        public Builder checksumSHA1(final String checksumSHA1) {
            this.checksumSHA1 = checksumSHA1;
            return this;
        }

        public Builder checksumSHA256(final String checksumSHA256) {
            this.checksumSHA256 = checksumSHA256;
            return this;
        }

        public ObjectMetadata build() {
            return new ObjectMetadata(eTag, contentLength, lastModified, contentType, userMetadata,
                    checksumCRC32, checksumCRC32C, checksumCRC64NVME, checksumSHA1, checksumSHA256);
        }
    }

    public static ObjectMetadata from(final File file) {
        return from(file.toPath());
    }

    public static ObjectMetadata from(final Path path) {
        final String contentType = contentType(path.getFileName().toString(), "application/octet-stream");
        try {
            return builder()
                    .contentLength(Files.size(path))
                    .lastModified(Files.getLastModifiedTime(path).toInstant())
                    .contentType(contentType)
                    .build();
        } catch (final java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public static String contentType(final String fileName, final String defaultType) {
        // Types the JDK doesn't know or gets wrong
        if (fileName.endsWith(".tar.gz")) return "application/x-gzip";
        if (fileName.endsWith(".jar")) return "application/java-archive";
        if (fileName.endsWith(".war")) return "application/java-archive";
        if (fileName.endsWith(".ear")) return "application/java-archive";
        if (fileName.endsWith(".rar")) return "application/java-archive";
        if (fileName.endsWith(".pom")) return "application/xml";
        if (fileName.endsWith(".md5")) return "text/plain";
        if (fileName.endsWith(".sha1")) return "text/plain";
        if (fileName.endsWith(".sha256")) return "text/plain";
        if (fileName.endsWith(".sha512")) return "text/plain";
        if (fileName.endsWith(".asc")) return "text/plain";

        // Let the JDK handle common types (.html, .txt, .xml, .json, .png, .jpg, etc.)
        final String guessed = URLConnection.guessContentTypeFromName(fileName);
        if (guessed != null) return guessed;

        return defaultType;
    }

    public static ObjectMetadata fromHead(final HeadObjectResponse response) {
        return builder()
                .eTag(S3File.stripQuotes(response.eTag()))
                .contentLength(response.contentLength())
                .lastModified(response.lastModified())
                .contentType(response.contentType())
                .userMetadata(response.metadata())
                .checksumCRC32(response.checksumCRC32())
                .checksumCRC32C(response.checksumCRC32C())
                .checksumCRC64NVME(response.checksumCRC64NVME())
                .checksumSHA1(response.checksumSHA1())
                .checksumSHA256(response.checksumSHA256())
                .build();
    }

    public static ObjectMetadata fromGet(final GetObjectResponse response) {
        return builder()
                .eTag(S3File.stripQuotes(response.eTag()))
                .contentLength(response.contentLength())
                .lastModified(response.lastModified())
                .contentType(response.contentType())
                .userMetadata(response.metadata())
                .checksumCRC32(response.checksumCRC32())
                .checksumCRC32C(response.checksumCRC32C())
                .checksumCRC64NVME(response.checksumCRC64NVME())
                .checksumSHA1(response.checksumSHA1())
                .checksumSHA256(response.checksumSHA256())
                .build();
    }

    public static ObjectMetadata fromPut(final PutObjectResponse response, final long contentLength) {
        return builder()
                .eTag(S3File.stripQuotes(response.eTag()))
                .contentLength(contentLength)
                .checksumCRC32(response.checksumCRC32())
                .checksumCRC32C(response.checksumCRC32C())
                .checksumCRC64NVME(response.checksumCRC64NVME())
                .checksumSHA1(response.checksumSHA1())
                .checksumSHA256(response.checksumSHA256())
                .build();
    }

    public static ObjectMetadata fromListing(final S3Object summary) {
        return builder()
                .eTag(S3File.stripQuotes(summary.eTag()))
                .contentLength(summary.size())
                .lastModified(summary.lastModified())
                .build();
    }
}
