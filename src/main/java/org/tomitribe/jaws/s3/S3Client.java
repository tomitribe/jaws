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

import org.tomitribe.jaws.NoSuchBucketException;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class S3Client {
    private final software.amazon.awssdk.services.s3.S3Client s3;
    private final S3AsyncClient s3Async;
    private final S3TransferManager transferManager;

    public S3Client(final software.amazon.awssdk.services.s3.S3Client s3, final S3AsyncClient s3Async) {
        this.s3 = s3;
        this.s3Async = s3Async;
        this.transferManager = S3TransferManager.builder()
                .s3Client(s3Async)
                .executor(createDefaultExecutorService())
                .build();
    }

    private static Executor createDefaultExecutorService() {
        ThreadFactory threadFactory = new ThreadFactory() {
            private int threadCount = 1;

            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("s3-transfer-manager-worker-" + threadCount++);
                return thread;
            }
        };
        return Executors.newFixedThreadPool(10, threadFactory);
    }

    public static <T> Stream<T> asStream(final Iterator<T> iterator) {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED),
                false);
    }

    public S3Bucket createBucket(final String name) {
        s3.createBucket(CreateBucketRequest.builder().bucket(name).build());

        final Bucket bucket = s3.listBuckets().buckets().stream()
                .filter(item -> name.equals(item.name()))
                .findAny()
                .orElseThrow(() -> new NoSuchBucketException(name));

        return new S3Bucket(this, bucket);
    }

    public S3Bucket getBucket(final String name) {
        final ListBucketsResponse bucketsResponse = s3.listBuckets();

        final Bucket bucket = bucketsResponse.buckets().stream()
                .filter(item -> name.equals(item.name()))
                .findAny()
                .orElseThrow(() -> new NoSuchBucketException(name));

        return new S3Bucket(this, bucket);
    }

    public Stream<S3Bucket> buckets() {
        return s3.listBuckets().buckets().stream()
                .map(bucket -> new S3Bucket(this, bucket));
    }

    public software.amazon.awssdk.services.s3.S3Client getS3() {
        return s3;
    }

    public S3AsyncClient getS3Async() {
        return s3Async;
    }

    public S3TransferManager getTransferManager() {
        return transferManager;
    }
}
