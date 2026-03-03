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

import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class S3Asserts {

    private final S3AsyncClient s3;
    private final String bucketName;

    private S3Asserts(final S3AsyncClient s3, final String bucketName) {
        this.s3 = s3;
        this.bucketName = bucketName;
    }

    public static S3Asserts of(final S3AsyncClient s3, final String bucketName) {
        return new S3Asserts(s3, bucketName);
    }

    public Snapshot snapshot() {
        final Map<String, S3Object> entries = listObjects().stream()
                .filter(o -> !o.key().endsWith("/"))
                .collect(Collectors.toMap(S3Object::key, Function.identity()));
        return new Snapshot(s3, bucketName, entries);
    }

    public String listing() {
        return listObjects().stream()
                .filter(o -> !o.key().endsWith("/"))
                .sorted((a, b) -> a.key().compareTo(b.key()))
                .map(o -> String.format("%-38s %s", stripQuotes(o.eTag()), o.key()))
                .collect(Collectors.joining("\n"));
    }

    public S3Asserts assertListing(final String expected) {
        assertEquals(expected, listing());
        return this;
    }

    public S3Asserts assertContent(final String key, final String expected) {
        assertEquals(expected, getObjectAsString(key));
        return this;
    }

    public S3Asserts assertExists(final String key) {
        try {
            join(s3.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key).build()));
        } catch (final S3Exception e) {
            if (e.statusCode() == 404) {
                fail("Expected object to exist: " + bucketName + "/" + key);
            }
            throw e;
        }
        return this;
    }

    public S3Asserts assertNotExists(final String key) {
        try {
            join(s3.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key).build()));
            fail("Expected object to NOT exist: " + bucketName + "/" + key);
        } catch (final S3Exception e) {
            if (e.statusCode() != 404) throw e;
        }
        return this;
    }

    private List<S3Object> listObjects() {
        final List<S3Object> objects = new ArrayList<>();
        ListObjectsRequest request = ListObjectsRequest.builder().bucket(bucketName).build();

        while (true) {
            final ListObjectsResponse response = join(s3.listObjects(request));
            objects.addAll(response.contents());

            if (!response.isTruncated()) break;

            String marker = response.nextMarker();
            if (marker == null) {
                final List<S3Object> contents = response.contents();
                if (!contents.isEmpty()) {
                    marker = contents.get(contents.size() - 1).key();
                }
            }
            request = request.toBuilder().marker(marker).build();
        }

        return objects;
    }

    private String getObjectAsString(final String key) {
        try (final InputStream stream = join(s3.getObject(
                GetObjectRequest.builder().bucket(bucketName).key(key).build(),
                AsyncResponseTransformer.toBlockingInputStream()))) {
            return new String(stream.readAllBytes());
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String stripQuotes(final String eTag) {
        if (eTag == null) return null;
        if (eTag.startsWith("\"") && eTag.endsWith("\"")) {
            return eTag.substring(1, eTag.length() - 1);
        }
        return eTag;
    }

    static <T> T join(final CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (final CompletionException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw e;
        }
    }

    public static class Snapshot {

        private final S3AsyncClient s3;
        private final String bucketName;
        private final Map<String, S3Object> entries;

        private Snapshot(final S3AsyncClient s3, final String bucketName, final Map<String, S3Object> entries) {
            this.s3 = s3;
            this.bucketName = bucketName;
            this.entries = entries;
        }

        public Snapshot assertContent(final String key, final String expected) {
            final S3Object object = entries.get(key);
            assertNotNull("Expected object to exist: " + key, object);
            try (final InputStream stream = S3Asserts.join(s3.getObject(
                    GetObjectRequest.builder().bucket(bucketName).key(key).build(),
                    AsyncResponseTransformer.toBlockingInputStream()))) {
                assertEquals(expected, new String(stream.readAllBytes()));
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
            return this;
        }

        public Snapshot assertExists(final String key) {
            assertNotNull("Expected object to exist: " + key, entries.get(key));
            return this;
        }

        public Snapshot assertNotExists(final String key) {
            assertNull("Expected object to NOT exist: " + key, entries.get(key));
            return this;
        }
    }
}
