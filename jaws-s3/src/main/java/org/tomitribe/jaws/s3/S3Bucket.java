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

import org.tomitribe.util.IO;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.FileDownload;
import software.amazon.awssdk.transfer.s3.model.Upload;
import software.amazon.awssdk.transfer.s3.model.UploadRequest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

public class S3Bucket {
    private final S3Client client;
    private final Bucket bucket;
    private final S3AsyncClient s3;

    public S3Bucket(final S3Client client, final Bucket bucket) {
        this.client = client;
        this.bucket = bucket;
        s3 = client.getS3();
    }

    public S3Client getClient() {
        return client;
    }

    public S3File asFile() {
        return S3File.rootFile(this);
    }

    public Stream<S3File> objects() {
        final ObjectListingIterator iterator = new ObjectListingIterator(bucket.name());
        return S3Client.asStream(iterator)
                .map(s3Object -> new S3File(this, s3Object));
    }

    public Stream<S3File> objects(final ListObjectsRequest request) {
        final ListObjectsRequest adjusted = request.toBuilder()
                .bucket(bucket.name())
                .build();
        final ObjectListingIterator iterator = new ObjectListingIterator(adjusted);
        return S3Client.asStream(iterator)
                .map(s3Object -> new S3File(this, s3Object));
    }

    public PutObjectResponse putObject(final String key, final File file) {
        return S3Client.join(s3.putObject(
                PutObjectRequest.builder().bucket(bucket.name()).key(key).build(),
                AsyncRequestBody.fromFile(file)));
    }

    public PutObjectResponse putObject(final String key, final InputStream inputStream, final long contentLength) {
        return S3Client.join(s3.putObject(
                PutObjectRequest.builder().bucket(bucket.name()).key(key).contentLength(contentLength).build(),
                AsyncRequestBody.fromInputStream(inputStream, contentLength, client.getExecutor())));
    }

    public PutObjectResponse putObject(final String key, final String content) {
        return S3Client.join(s3.putObject(
                PutObjectRequest.builder().bucket(bucket.name()).key(key).build(),
                AsyncRequestBody.fromString(content)));
    }

    public ResponseInputStream<GetObjectResponse> getObject(final String key) {
        return S3Client.join(s3.getObject(GetObjectRequest.builder().bucket(bucket.name()).key(key).build(),
                AsyncResponseTransformer.toBlockingInputStream()));
    }

    public HeadObjectResponse getObjectMetadata(final String key) {
        return S3Client.join(s3.headObject(HeadObjectRequest.builder().bucket(bucket.name()).key(key).build()));
    }

    public S3File getFile(final String key) {
        return new S3File(this, key, getObjectMetadata(key));
    }

    /**
     * Favor S3File.getValueAsStream() over this method.
     * <p>
     * The S3 getObject call that powers this method will
     * issue an HTTP request to fetch the content.
     * <p>
     * The S3File.getValueAsStream() will make the same request, however, the
     * response metadata will be kept afterwards allowing for calls to get other
     * information about the content such as lastModified or etag to be zero cost.
     * Additionally, any subsequent calls to get the content again will now be
     * just 1 request.
     */
    public InputStream getObjectAsStream(final String key) {
        try {
            return S3Client.join(s3.getObject(GetObjectRequest.builder().bucket(bucket.name()).key(key).build(),
                    AsyncResponseTransformer.toBlockingInputStream()));
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new NoSuchS3ObjectException(bucket.name(), key, e);
            }
            throw e;
        }
    }

    /**
     * Favor S3File.getValueAsString() over this method.
     * <p>
     * The S3 getObject call that powers this method will
     * issue an HTTP request to fetch the content.
     * <p>
     * The S3File.getValueAsString() will make the same request, however, the
     * response metadata will be kept afterwards allowing for calls to get other
     * information about the content such as lastModified or etag to be zero cost.
     * Additionally, any subsequent calls to get the content again will now be
     * just 1 request.
     */
    public String getObjectAsString(final String key) {
        try {
            final ResponseInputStream<GetObjectResponse> stream = S3Client.join(s3.getObject(
                    GetObjectRequest.builder().bucket(bucket.name()).key(key).build(),
                    AsyncResponseTransformer.toBlockingInputStream()));
            try (stream) {
                return IO.slurp(stream);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new NoSuchS3ObjectException(bucket.name(), key, e);
            }
            throw e;
        }
    }

    public Upload upload(final String key, final InputStream input, final long size) {
        return client.getTransferManager().upload(UploadRequest.builder()
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(bucket.name())
                        .key(key)
                        .contentLength(size)
                        .build())
                .requestBody(AsyncRequestBody.fromInputStream(input, size, client.getExecutor()))
                .build());
    }

    public Upload upload(final String key, final File file) {
        return client.getTransferManager().upload(UploadRequest.builder()
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(bucket.name())
                        .key(key)
                        .build())
                .requestBody(AsyncRequestBody.fromFile(file))
                .build());
    }

    public FileDownload download(final String key, final File destination) {
        return client.getTransferManager().downloadFile(DownloadFileRequest.builder()
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucket.name())
                        .key(key)
                        .build())
                .destination(destination.toPath())
                .build());
    }

    public PutObjectResponse setObjectAsString(final String key, final String value) {
        return S3Client.join(s3.putObject(
                PutObjectRequest.builder().bucket(bucket.name()).key(key).build(),
                AsyncRequestBody.fromString(value)));
    }

    public PutObjectResponse setObjectAsFile(final String key, final File value) {
        return S3Client.join(s3.putObject(
                PutObjectRequest.builder().bucket(bucket.name()).key(key).build(),
                AsyncRequestBody.fromFile(value)));
    }

    public PutObjectResponse setObjectAsStream(final String key, final InputStream value) {
        try {
            final byte[] bytes = value.readAllBytes();
            return S3Client.join(s3.putObject(
                    PutObjectRequest.builder().bucket(bucket.name()).key(key).build(),
                    AsyncRequestBody.fromBytes(bytes)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void deleteObject(final String key) {
        S3Client.join(s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket.name()).key(key).build()));
    }

    public Instant getCreationDate() {
        return bucket.creationDate();
    }

    public String getName() {
        return bucket.name();
    }

    class ObjectListingIterator implements Iterator<S3Object> {

        private Iterator<S3Object> iterator;
        private ListObjectsResponse response;
        private ListObjectsRequest request;

        public ObjectListingIterator(final String bucketName) {
            this.request = ListObjectsRequest.builder().bucket(bucketName).build();
            this.response = S3Client.join(s3.listObjects(request));
            this.iterator = response.contents().iterator();
        }

        public ObjectListingIterator(final ListObjectsRequest request) {
            this.request = request;
            this.response = S3Client.join(s3.listObjects(request));
            this.iterator = response.contents().iterator();
        }

        @Override
        public boolean hasNext() {
            if (iterator.hasNext()) return true;
            if (!response.isTruncated()) return false;

            String marker = response.nextMarker();
            if (marker == null) {
                final List<S3Object> contents = response.contents();
                if (!contents.isEmpty()) {
                    marker = contents.get(contents.size() - 1).key();
                }
            }
            request = request.toBuilder().marker(marker).build();
            response = S3Client.join(s3.listObjects(request));
            iterator = response.contents().iterator();

            return hasNext();
        }

        @Override
        public S3Object next() {
            return iterator.next();
        }
    }
}
