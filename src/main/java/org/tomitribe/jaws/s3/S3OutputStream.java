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

import alex.mojaki.s3upload.MultiPartOutputStream;
import alex.mojaki.s3upload.StreamTransferManager;
import com.amazonaws.services.s3.AmazonS3;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class S3OutputStream extends OutputStream {

    private final OutputStream out;
    private final StreamTransferManager manager;
    private final Runnable completedCallback;

    public S3OutputStream(final AmazonS3 client, final String bucket, final String key) {
        this(client, bucket, key, () -> {
        });
    }

    public S3OutputStream(final AmazonS3 client, final String bucket, final String key, final Runnable runnable) {
        this.manager = new StreamTransferManager(bucket, key, client)
                .numStreams(1)
                .numUploadThreads(1)
                .queueCapacity(2)
                .partSize(10);
        final List<MultiPartOutputStream> streams = manager.getMultiPartOutputStreams();
        this.out = streams.get(0);
        this.completedCallback = runnable;
    }

    @Override
    public void write(final int b) throws IOException {
        out.write(b);
    }

    @Override
    public void write(final byte[] b) throws IOException {
        out.write(b);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        out.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        try {
            out.close();
        } finally {
            manager.complete();
            completedCallback.run();
        }
    }
}
