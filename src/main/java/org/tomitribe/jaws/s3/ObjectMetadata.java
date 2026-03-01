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

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

public class ObjectMetadata {
    private final String eTag;
    private final long contentLength;
    private final Instant lastModified;
    private final String contentType;
    private final Map<String, String> userMetadata;

    public ObjectMetadata(final String eTag, final long contentLength, final Instant lastModified, final String contentType, final Map<String, String> userMetadata) {
        this.eTag = eTag;
        this.contentLength = contentLength;
        this.lastModified = lastModified;
        this.contentType = contentType;
        this.userMetadata = userMetadata;
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

    public static ObjectMetadata fromHead(final HeadObjectResponse response) {
        return new ObjectMetadata(
                S3File.stripQuotes(response.eTag()),
                response.contentLength(),
                response.lastModified(),
                response.contentType(),
                response.metadata()
        );
    }

    public static ObjectMetadata fromGet(final GetObjectResponse response) {
        return new ObjectMetadata(
                S3File.stripQuotes(response.eTag()),
                response.contentLength(),
                response.lastModified(),
                response.contentType(),
                response.metadata()
        );
    }

    public static ObjectMetadata fromPut(final PutObjectResponse response, final long contentLength) {
        return new ObjectMetadata(
                S3File.stripQuotes(response.eTag()),
                contentLength,
                null,
                null,
                null
        );
    }

    public static ObjectMetadata fromListing(final S3Object summary) {
        return new ObjectMetadata(
                S3File.stripQuotes(summary.eTag()),
                summary.size(),
                summary.lastModified(),
                null,
                null
        );
    }
}
