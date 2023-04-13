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

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import org.tomitribe.jaws.NoSuchBucketException;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class S3Client {
    private final AmazonS3 s3;

    private final TransferManager transferManager;

    public S3Client(final AmazonS3 s3) {
        this.s3 = s3;
        this.transferManager = TransferManagerBuilder.standard()
                .withS3Client(s3)
                .withExecutorFactory(S3Client::createDefaultExecutorService)
                .build();
    }

    private static ThreadPoolExecutor createDefaultExecutorService() {
        ThreadFactory threadFactory = new ThreadFactory() {
            private int threadCount = 1;

            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("s3-transfer-manager-worker-" + threadCount++);
                return thread;
            }
        };
        return (ThreadPoolExecutor) Executors.newFixedThreadPool(10, threadFactory);
    }

    public static <T> Stream<T> asStream(final Iterator<T> iterator) {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED),
                false);
    }

    public S3Bucket createBucket(final String s) throws SdkClientException, AmazonServiceException {
        final Bucket bucket = s3.createBucket(s);
        return new S3Bucket(this, bucket);
    }

    public S3Bucket getBucket(final String name) {
        /*
         * There doesn't seem to be a way to get a bucket
         * directly by name.
         */
        final Bucket bucket = s3.listBuckets().stream()
                .filter(item -> name.equals(item.getName()))
                .findAny()
                .orElseThrow(() -> new NoSuchBucketException(name));

        return new S3Bucket(this, bucket);
    }

    public Stream<S3Bucket> buckets() {
        return s3.listBuckets().stream()
                .map(bucket -> new S3Bucket(this, bucket));

    }

    public AmazonS3 getS3() {
        return s3;
    }

    public TransferManager getTransferManager() {
        return transferManager;
    }
}
