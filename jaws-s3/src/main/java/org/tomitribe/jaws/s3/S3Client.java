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
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Entry point for interacting with Amazon S3. Wraps an
 * {@link S3AsyncClient} and an {@link S3TransferManager},
 * providing bucket-level operations and a shared thread pool
 * for asynchronous transfers.
 *
 * <p>Typical usage:
 * <pre>{@code
 * S3AsyncClient asyncClient = S3AsyncClient.builder().build();
 * S3Client s3 = new S3Client(asyncClient);
 *
 * S3Bucket bucket = s3.getBucket("my-bucket");
 * Catalog catalog = bucket.as(Catalog.class);
 * }</pre>
 */
public class S3Client {
    private final S3AsyncClient s3;
    private final S3TransferManager transferManager;
    private final ExecutorService executor;

    /**
     * Creates a new S3Client backed by the given async client.
     * A default thread pool and {@link S3TransferManager} are
     * created automatically.
     *
     * @param s3 the AWS async S3 client
     */
    public S3Client(final S3AsyncClient s3) {
        this.s3 = s3;
        this.executor = createDefaultExecutorService();
        this.transferManager = S3TransferManager.builder()
                .s3Client(s3)
                .build();
    }

    private static ThreadPoolExecutor createDefaultExecutorService() {
        final ThreadFactory threadFactory = new ThreadFactory() {
            private int threadCount = 1;

            public Thread newThread(final Runnable r) {
                final Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("s3-transfer-manager-worker-" + threadCount++);
                return thread;
            }
        };
        return (ThreadPoolExecutor) Executors.newFixedThreadPool(10, threadFactory);
    }

    /**
     * Converts an {@link Iterator} into an ordered, sequential {@link Stream}.
     *
     * @param iterator the iterator to wrap
     * @param <T>      the element type
     * @return a stream over the iterator's elements
     */
    static <T> Stream<T> asStream(final Iterator<T> iterator) {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED),
                false);
    }

    /**
     * Creates a new S3 bucket with the given name.
     *
     * @param s the bucket name
     * @return the newly created bucket
     */
    public S3Bucket createBucket(final String s) {
        join(s3.createBucket(CreateBucketRequest.builder().bucket(s).build()));
        final Bucket bucket = Bucket.builder().name(s).build();
        return new S3Bucket(this, bucket);
    }

    /**
     * Returns the bucket with the given name.
     *
     * @param name the bucket name
     * @return the matching bucket
     * @throws org.tomitribe.jaws.NoSuchBucketException if no bucket with
     *         that name exists
     */
    public S3Bucket getBucket(final String name) {
        final Bucket bucket = join(s3.listBuckets()).buckets().stream()
                .filter(item -> name.equals(item.name()))
                .findAny()
                .orElseThrow(() -> new NoSuchBucketException(name));

        return new S3Bucket(this, bucket);
    }

    /**
     * Returns all buckets visible to the configured AWS credentials.
     *
     * @return a stream of buckets
     */
    public Stream<S3Bucket> buckets() {
        return join(s3.listBuckets()).buckets().stream()
                .map(bucket -> new S3Bucket(this, bucket));

    }

    /**
     * Returns the underlying AWS async S3 client.
     *
     * @return the async S3 client
     */
    public S3AsyncClient getS3() {
        return s3;
    }

    /**
     * Returns the {@link S3TransferManager} used for upload and
     * download operations.
     *
     * @return the transfer manager
     */
    public S3TransferManager getTransferManager() {
        return transferManager;
    }

    /**
     * Returns the shared executor service used for async transfers.
     *
     * @return the executor service
     */
    ExecutorService getExecutor() {
        return executor;
    }

    static <T> T join(final CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw e;
        }
    }

    public static S3Client from(final S3AsyncClient client) {
        return new S3Client(client);
    }
}
