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
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
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
import software.amazon.awssdk.transfer.s3.progress.TransferListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.tomitribe.jaws.s3.S3Client.asStream;

/**
 * A stable reference to an S3 object, analogous to {@link java.io.File}.
 *
 * <p>Like {@code java.io.File}, an {@code S3File} can refer to an S3
 * object before it exists, once it is created, after it is updated,
 * and after it is deleted. Throughout these state transitions callers
 * may hold the same instance and it will reflect the most current
 * information that has been fetched.
 *
 * <p>S3File instances are obtained from {@link S3Bucket#getFile(String)},
 * {@link S3Bucket#root()}, or through the proxy layer via
 * {@link S3#file()} and {@link S3.Dir#file(String)}.
 *
 * <p>To create a strongly-typed proxy from an S3File, use
 * {@link #as(Class)}:
 * <pre>{@code
 * S3File file = bucket.getFile("config.json");
 * Config config = file.as(Config.class);
 * }</pre>
 */
public class S3File {

    private final S3Bucket bucket;
    private final Path path;
    private final AtomicReference<Node> node = new AtomicReference<>();

    static String stripQuotes(final String eTag) {
        if (eTag == null) return null;
        if (eTag.startsWith("\"") && eTag.endsWith("\"")) {
            return eTag.substring(1, eTag.length() - 1);
        }
        return eTag;
    }

    S3File(final S3Bucket bucket, final S3Object summary) {
        this.bucket = bucket;
        this.path = Path.fromKey(summary.key());
        this.node.set(new ObjectSummary(summary));
    }

    S3File(final S3Bucket bucket, final String key, final HeadObjectResponse response) {
        this.bucket = bucket;
        this.path = Path.fromKey(key);
        this.node.set(new Metadata(response.eTag(), response.contentLength(), response.lastModified(), response.contentType(), response.metadata()));
    }

    S3File(final S3Bucket bucket, final Path path, final Class<? extends Node> type) {
        this.bucket = bucket;
        this.path = path;
        this.node.set(nodeInstance(type));
    }

    private Node nodeInstance(final Class<? extends Node> type) {
        if (Directory.class.equals(type)) return new Directory();
        if (NewObject.class.equals(type)) return new NewObject();
        if (Unknown.class.equals(type)) return new Unknown();

        throw new IllegalArgumentException("Unsupported node type: " + type.getSimpleName());
    }

    static S3File rootFile(final S3Bucket bucket) {
        return new S3File(bucket, Path.ROOT, Directory.class);
    }

    /**
     * Returns the parsed path of this S3 object within its bucket.
     *
     * @return the object path
     */
    public Path getPath() {
        return path;
    }

    /**
     * Returns {@code true} if this object is known to exist in S3.
     *
     * @return {@code true} if the object exists
     */
    public boolean exists() {
        return node.get().exists();
    }

    /**
     * Returns {@code true} if this entry represents a file (object),
     * as opposed to a directory (common prefix).
     *
     * @return {@code true} if this is a file
     */
    public boolean isFile() {
        return node.get().isFile();
    }

    /**
     * Returns {@code true} if this entry represents a directory
     * (common prefix), as opposed to a file (object).
     *
     * @return {@code true} if this is a directory
     */
    public boolean isDirectory() {
        return node.get().isDirectory();
    }

    /**
     * Returns the parent directory of this S3 object, or {@code null}
     * if this is the bucket root.
     *
     * @return the parent S3File, or {@code null}
     */
    public S3File getParentFile() {
        final Path parent = path.getParent();
        if (parent == null) return null;
        return new S3File(bucket, parent, Directory.class);
    }

    /**
     * Returns a child S3File relative to this directory.
     * The child may or may not exist in S3.
     *
     * @param name the child key name
     * @return the child S3File
     */
    public S3File getFile(final String name) {
        return node.get().getFile(name);
    }

    /**
     * Returns all objects under this directory recursively as a flat
     * listing (no delimiter).
     *
     * @return a stream of all descendant S3Files
     */
    public Stream<S3File> files() {
        return node.get().files();
    }

    /**
     * Returns objects matching the given {@link ListObjectsRequest}.
     * The request's bucket is set automatically.
     *
     * @param request the listing request with prefix, delimiter, etc.
     * @return a stream of matching S3Files
     */
    public Stream<S3File> files(final ListObjectsRequest request) {
        return node.get().files(request);
    }

    /**
     * Walks the directory tree rooted at this S3File with no depth limit,
     * using {@code "/"} as the delimiter.
     *
     * @return a stream of all descendant S3Files including directories
     */
    public Stream<S3File> walk() {
        return node.get().walk(WalkingIterator.INFINITE, "/");
    }

    /**
     * Walks the directory tree rooted at this S3File up to the given depth,
     * using {@code "/"} as the delimiter.
     *
     * @param maxDepth the maximum depth to traverse
     * @return a stream of descendant S3Files up to the specified depth
     */
    public Stream<S3File> walk(final int maxDepth) {
        return node.get().walk(maxDepth, "/");
    }

    /**
     * Walks the directory tree rooted at this S3File with no depth limit,
     * using the given delimiter.
     *
     * @param delimiter the delimiter for splitting the key hierarchy
     * @return a stream of all descendant S3Files including directories
     */
    Stream<S3File> walk(final String delimiter) {
        return node.get().walk(WalkingIterator.INFINITE, delimiter);
    }

    /**
     * Walks the directory tree rooted at this S3File up to the given depth,
     * using the given delimiter.
     *
     * @param maxDepth  the maximum depth to traverse
     * @param delimiter the delimiter for splitting the key hierarchy
     * @return a stream of descendant S3Files up to the specified depth
     */
    Stream<S3File> walk(final int maxDepth, final String delimiter) {
        return node.get().walk(maxDepth, delimiter);
    }

    /**
     * Walks the directory tree with no depth limit, using the given
     * delimiter and request (which may carry a prefix to narrow results).
     *
     * @param request   the base listing request (prefix, etc.)
     * @param delimiter the delimiter for splitting the key hierarchy
     * @return a stream of all descendant S3Files including directories
     */
    Stream<S3File> walk(final ListObjectsRequest request, final String delimiter) {
        return node.get().walk(request, WalkingIterator.INFINITE, delimiter);
    }

    /**
     * Walks the directory tree up to the given depth, using the given
     * delimiter and request (which may carry a prefix to narrow results).
     *
     * @param request   the base listing request (prefix, etc.)
     * @param maxDepth  the maximum depth to traverse
     * @param delimiter the delimiter for splitting the key hierarchy
     * @return a stream of descendant S3Files up to the specified depth
     */
    Stream<S3File> walk(final ListObjectsRequest request, final int maxDepth, final String delimiter) {
        return node.get().walk(request, maxDepth, delimiter);
    }

    /**
     * Returns immediate child files and directories (one level deep)
     * using a delimiter-based listing.
     *
     * @return a stream of immediate child S3Files
     */
    Stream<S3File> list() {
        return node.get().list();
    }

    /**
     * Returns the full key of this object within its bucket.
     * For the bucket root this returns an empty string.
     *
     * @return the absolute object key
     */
    public String getAbsoluteName() {
        return path.getAbsoluteName();
    }

    /**
     * Returns the simple name (last path segment) of this object.
     *
     * @return the object name
     */
    public String getName() {
        return path.getName();
    }

    /**
     * Returns the bucket that contains this S3 object.
     *
     * @return the parent bucket
     */
    public S3Bucket getBucket() {
        return bucket;
    }

    /**
     * Returns the object content as an {@link InputStream}.
     * The caller is responsible for closing the stream.
     *
     * @return an input stream of the object content
     */
    public InputStream getValueAsStream() {
        return node.get().getValueAsStream();
    }

    /**
     * Returns the object content as a {@link String}.
     *
     * @return the object content
     */
    public String getValueAsString() {
        return node.get().getValueAsString();
    }

    /**
     * Replaces the object content by reading from the given input stream.
     *
     * @param inputStream the input stream to read from
     */
    public void setValueAsStream(final InputStream inputStream) {
        node.get().setValueAsStream(inputStream);
    }

    /**
     * Replaces the object content with the contents of the given file.
     *
     * @param file the local file whose content will be uploaded
     */
    public void setValueAsFile(final File file) {
        node.get().setValueAsFile(file);
    }

    /**
     * Replaces the object content with the given string.
     *
     * @param value the new content
     */
    public void setValueAsString(final String value) {
        node.get().setValueAsString(value);
    }

    /**
     * Returns the name of the bucket containing this object.
     *
     * @return the bucket name
     */
    public String getBucketName() {
        return bucket.getName();
    }

    /**
     * Returns the ETag of the object, typically an MD5 hash of the content.
     *
     * @return the ETag, or {@code null} if not yet fetched
     */
    public String getETag() {
        return node.get().getETag();
    }

    /**
     * Returns the size of the object in bytes.
     *
     * @return the content length in bytes
     */
    public long getSize() {
        return node.get().getSize();
    }

    /**
     * Returns the last modified timestamp of the object.
     *
     * @return the last modified instant, or {@code null} if not available
     */
    public Instant getLastModified() {
        return node.get().getLastModified();
    }

    /**
     * Returns the full {@link ObjectMetadata} for this object,
     * including content type, user metadata, and other attributes.
     *
     * @return the object metadata
     */
    public ObjectMetadata getObjectMetadata() {
        return node.get().getObjectMetadata();
    }

    /**
     * Creates a strongly-typed proxy backed by this S3File. The given
     * class should be an interface extending {@link S3}, {@link S3.Dir},
     * or {@link S3.File}.
     *
     * <pre>{@code
     * Product product = s3File.as(Product.class);
     * }</pre>
     *
     * @param clazz the interface class to proxy
     * @param <T>   the proxy type
     * @return a proxy instance backed by this S3File
     */
    public <T> T as(final Class<T> clazz) {
        return (T) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class[]{clazz},
                new S3Handler(this)
        );
    }

    @Override
    public boolean equals(final java.lang.Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final S3File s3File = (S3File) o;

        if (!path.equals(s3File.path)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    /**
     * Deletes this object from S3.
     */
    public void delete() {
        node.get().delete();
    }

    /**
     * Deletes this object from S3.
     *
     * @param force if {@code true}, recursively deletes all children
     *              when this is a directory
     */
    public void delete(final boolean force) {
        node.get().delete(force);
    }

    /**
     * Uploads content from the given input stream.
     *
     * @param input the content to upload
     * @param size  the content length in bytes
     * @return the in-progress Upload
     */
    public Upload upload(final InputStream input, final long size) {
        return upload(input, size, null);
    }

    /**
     * Uploads content from the given input stream with a transfer listener.
     *
     * @param input    the content to upload
     * @param size     the content length in bytes
     * @param listener the listener for transfer progress events, or {@code null}
     * @return the in-progress Upload
     */
    public Upload upload(final InputStream input, final long size, final TransferListener listener) {
        return upload(input, ObjectMetadata.builder().contentLength(size).build(), listener);
    }

    /**
     * Uploads content from the given input stream with custom metadata.
     *
     * @param input          the content to upload
     * @param objectMetadata metadata including content length, type, and user metadata
     * @return the in-progress Upload
     */
    public Upload upload(final InputStream input, final ObjectMetadata objectMetadata) {
        return upload(input, objectMetadata, null);
    }

    /**
     * Uploads content from the given input stream with custom metadata
     * and a transfer listener.
     *
     * @param input          the content to upload
     * @param objectMetadata metadata including content length, type, and user metadata
     * @param listener       the listener for transfer progress events, or {@code null}
     * @return the in-progress Upload
     */
    public Upload upload(final InputStream input, final ObjectMetadata objectMetadata, final TransferListener listener) {
        final long size = objectMetadata.getContentLength();
        final AsyncRequestBody body = input != null
                ? AsyncRequestBody.fromInputStream(input, size, bucket.getClient().getExecutor())
                : AsyncRequestBody.empty();
        return upload(body, objectMetadata, listener);
    }

    /**
     * Uploads a local file. Content type and metadata are derived
     * automatically via {@link ObjectMetadata#from(File)}.
     *
     * @param file the local file to upload
     * @return the in-progress Upload
     */
    public Upload upload(final File file) {
        return upload(file, (TransferListener) null);
    }

    /**
     * Uploads a local file with a transfer listener. Content type and
     * metadata are derived automatically via {@link ObjectMetadata#from(File)}.
     *
     * @param file     the local file to upload
     * @param listener the listener for transfer progress events, or {@code null}
     * @return the in-progress Upload
     */
    public Upload upload(final File file, final TransferListener listener) {
        return upload(file, ObjectMetadata.from(file), listener);
    }

    /**
     * Uploads a local file with custom metadata.
     *
     * @param file           the local file to upload
     * @param objectMetadata metadata including content type and user metadata
     * @return the in-progress Upload
     */
    public Upload upload(final File file, final ObjectMetadata objectMetadata) {
        return upload(file, objectMetadata, null);
    }

    /**
     * Uploads a local file with custom metadata and a transfer listener.
     *
     * @param file           the local file to upload
     * @param objectMetadata metadata including content type and user metadata
     * @param listener       the listener for transfer progress events, or {@code null}
     * @return the in-progress Upload
     */
    public Upload upload(final File file, final ObjectMetadata objectMetadata, final TransferListener listener) {
        final AsyncRequestBody body = file != null
                ? AsyncRequestBody.fromFile(file)
                : AsyncRequestBody.empty();
        return upload(body, objectMetadata, listener);
    }

    /**
     * Uploads a local file from the given path. Content type and metadata
     * are derived automatically via {@link ObjectMetadata#from(java.nio.file.Path)}.
     *
     * @param path the local path to upload
     * @return the in-progress Upload
     */
    public Upload upload(final java.nio.file.Path path) {
        return upload(path, (TransferListener) null);
    }

    /**
     * Uploads a local file from the given path with a transfer listener.
     * Content type and metadata are derived automatically via
     * {@link ObjectMetadata#from(java.nio.file.Path)}.
     *
     * @param path     the local path to upload
     * @param listener the listener for transfer progress events, or {@code null}
     * @return the in-progress Upload
     */
    public Upload upload(final java.nio.file.Path path, final TransferListener listener) {
        return upload(path, ObjectMetadata.from(path), listener);
    }

    /**
     * Uploads a local file from the given path with custom metadata.
     *
     * @param path           the local path to upload
     * @param objectMetadata metadata including content type and user metadata
     * @return the in-progress Upload
     */
    public Upload upload(final java.nio.file.Path path, final ObjectMetadata objectMetadata) {
        return upload(path, objectMetadata, null);
    }

    /**
     * Uploads a local file from the given path with custom metadata
     * and a transfer listener.
     *
     * @param path           the local path to upload
     * @param objectMetadata metadata including content type and user metadata
     * @param listener       the listener for transfer progress events, or {@code null}
     * @return the in-progress Upload
     */
    public Upload upload(final java.nio.file.Path path, final ObjectMetadata objectMetadata, final TransferListener listener) {
        final AsyncRequestBody body = path != null
                ? AsyncRequestBody.fromFile(path)
                : AsyncRequestBody.empty();
        return upload(body, objectMetadata, listener);
    }

    private Upload upload(final AsyncRequestBody body, final ObjectMetadata objectMetadata, final TransferListener listener) {
        final PutObjectRequest.Builder putBuilder = PutObjectRequest.builder()
                .bucket(getBucketName())
                .key(getAbsoluteName())
                .contentLength(objectMetadata.getContentLength());

        if (objectMetadata.getContentType() != null) {
            putBuilder.contentType(objectMetadata.getContentType());
        }

        final Map<String, String> userMetadata = objectMetadata.getUserMetadata();
        if (!userMetadata.isEmpty()) {
            putBuilder.metadata(userMetadata);
        }

        final UploadRequest.Builder builder = UploadRequest.builder()
                .putObjectRequest(putBuilder.build())
                .requestBody(body);

        if (listener != null) {
            builder.addTransferListener(listener);
        }

        return node.get().upload(builder.build());
    }

    /**
     * Uploads using a fully-configured {@link UploadRequest}.
     *
     * @param uploadRequest the pre-built upload request
     * @return the in-progress Upload
     */
    public Upload upload(final UploadRequest uploadRequest) {
        return upload(uploadRequest, null);
    }

    /**
     * Uploads using a fully-configured {@link UploadRequest} with
     * a transfer listener.
     *
     * @param uploadRequest the pre-built upload request
     * @param listener      the listener for transfer progress events, or {@code null}
     * @return the in-progress Upload
     */
    public Upload upload(final UploadRequest uploadRequest, final TransferListener listener) {
        if (listener == null) {
            return node.get().upload(uploadRequest);
        }

        final UploadRequest withListener = uploadRequest.toBuilder()
                .addTransferListener(listener)
                .build();

        return node.get().upload(withListener);
    }

    /**
     * Downloads this S3 object to a local file.
     *
     * @param destination the local file to write to
     * @return the in-progress FileDownload
     */
    public FileDownload download(final File destination) {
        return download(destination, null);
    }

    /**
     * Downloads this S3 object to a local file with a transfer listener.
     *
     * @param destination the local file to write to
     * @param listener    the listener for transfer progress events, or {@code null}
     * @return the in-progress FileDownload
     */
    public FileDownload download(final File destination, final TransferListener listener) {
        final DownloadFileRequest.Builder builder = DownloadFileRequest.builder()
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(getBucketName())
                        .key(getAbsoluteName())
                        .build())
                .destination(destination.toPath());

        if (listener != null) {
            builder.addTransferListener(listener);
        }

        return node.get().download(builder.build());
    }

    /**
     * AmazonS3 API has a very large number of ways to get at
     * the same data.
     * <p>
     * For example if you look up a single object in S3 you will
     * get an S3Object instance.  If you list objects in S3 you
     * will get several S3ObjectSummary instances.  If you update
     * an object in S3 you will get a PutObjectResult.
     * <p>
     * This interface serves as a way to adapt them all to one
     * common interface, making all these variations manageable.
     * <p>
     * The name 'Node' is somewhat inspired by the inode concept
     * of the unix file system.
     */
    private interface Node {

        boolean isFile();

        boolean isDirectory();

        boolean exists();

        S3File getFile(final String name);

        default Stream<S3File> files() {
            return Stream.of();
        }

        default Stream<S3File> files(final ListObjectsRequest request) {
            return Stream.of();
        }

        default Stream<S3File> walk(final int maxDepth, final String delimiter) {
            return Stream.of();
        }

        default Stream<S3File> walk(final ListObjectsRequest request, final int maxDepth, final String delimiter) {
            return Stream.of();
        }

        default Stream<S3File> list() {
            return Stream.of();
        }

        InputStream getValueAsStream();

        String getValueAsString();

        void setValueAsStream(final InputStream inputStream);

        void setValueAsString(final String value);

        void setValueAsFile(final File file);

        String getETag();

        long getSize();

        Instant getLastModified();

        default void delete() {
            delete(false);
        }

        void delete(final boolean force);

        Upload upload(UploadRequest request);

        FileDownload download(DownloadFileRequest request);

        ObjectMetadata getObjectMetadata();
    }

    /**
     * S3 does not actually have the concept of directories.
     * <p>
     * They can be implied, however, as you are allowed to
     * use slashes in the names of Objects as well as query
     * Objects that live only under a specific prefix (path).
     * <p>
     * This Node implementation attempts to enforce/invent
     * some structure that can more strongly imply directories.
     */
    private class Directory implements Node {

        @Override
        public boolean exists() {
            return true; // TODO perhaps not the best default
        }

        @Override
        public boolean isFile() {
            return false;
        }

        @Override
        public boolean isDirectory() {
            return true;
        }

        @Override
        public Stream<S3File> files() {
            return bucket.objects(ListObjectsRequest.builder().prefix(path.getSearchPrefix()).build());
        }

        @Override
        public Stream<S3File> files(final ListObjectsRequest request) {
            return listRequest(request);
        }

        @Override
        public S3File getFile(final String name) {
            final Path child = path.getChild(name);
            return new S3File(bucket, child, Unknown.class);
        }

        @Override
        public Stream<S3File> walk(final int maxDepth, final String delimiter) {
            return performWalk(maxDepth, delimiter);
        }

        @Override
        public Stream<S3File> walk(final ListObjectsRequest request, final int maxDepth, final String delimiter) {
            return performWalk(request, maxDepth, delimiter);
        }

        @Override
        public Stream<S3File> list() {
            return performSingleLevelListing();
        }

        @Override
        public InputStream getValueAsStream() {
            throw new UnsupportedOperationException("S3File refers to a directory");
        }

        @Override
        public String getValueAsString() {
            throw new UnsupportedOperationException("S3File refers to a directory");
        }

        @Override
        public void setValueAsStream(final InputStream inputStream) {
            throw new UnsupportedOperationException("S3File refers to a directory");
        }

        @Override
        public void setValueAsString(final String value) {
            throw new UnsupportedOperationException("S3File refers to a directory");
        }

        @Override
        public void setValueAsFile(final File file) {
            throw new UnsupportedOperationException("S3File refers to a directory");
        }

        @Override
        public String getETag() {
            throw new UnsupportedOperationException("S3File refers to a directory");
        }

        @Override
        public long getSize() {
            throw new UnsupportedOperationException("S3File refers to a directory");
        }

        @Override
        public Instant getLastModified() {
            throw new UnsupportedOperationException("S3File refers to a directory");
        }

        @Override
        public void delete(final boolean force) {
            if (!files().findAny().isPresent()) { // not possible or it's not a Directory object but in case of a bug ...
                return;
            }

            if (!force) {
                throw new UnsupportedOperationException("S3File refers to a non empty directory. Use force delete flag.");

            } else {
                files().forEach(S3File::delete);
                node.compareAndSet(this, new NewObject());

            }
        }

        @Override
        public Upload upload(final UploadRequest request) {
            throw new UnsupportedOperationException("S3File refers to a directory");
        }

        @Override
        public FileDownload download(final DownloadFileRequest request) {
            throw new UnsupportedOperationException("S3File refers to a directory");
        }

        @Override
        public ObjectMetadata getObjectMetadata() {
            throw new UnsupportedOperationException("S3File refers to a directory");
        }
    }

    /**
     * When looking up a single object, the Amazon S3 API
     * will return metadata.  This serves as
     * the Node adapter for that form of representing the
     * common metadata.
     */
    private class Metadata implements Node {
        private final String eTag;
        private final long contentLength;
        private final Instant lastModified;
        private final String contentType;
        private final Map<String, String> userMetadata;

        public Metadata(final String eTag, final long contentLength, final Instant lastModified, final String contentType, final Map<String, String> userMetadata) {
            this.eTag = stripQuotes(eTag);
            this.contentLength = contentLength;
            this.lastModified = lastModified;
            this.contentType = contentType;
            this.userMetadata = userMetadata;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public boolean isFile() {
            return true;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public S3File getFile(final String name) {
            final String message = String.format("S3File '%s' is a not directory and cannot have child '%s'", path.getAbsoluteName(), name);
            throw new UnsupportedOperationException(message);
        }

        @Override
        public InputStream getValueAsStream() {
            return openStreamAndReplace(this);
        }

        @Override
        public String getValueAsString() {
            return readAndReplace(this);
        }

        @Override
        public void setValueAsStream(final InputStream inputStream) {
            writeStreamAndReplace(this, inputStream);
        }

        @Override
        public void setValueAsString(final String value) {
            writeStringAndReplace(this, value);
        }

        @Override
        public void setValueAsFile(final File value) {
            writeFileAndReplace(this, value);
        }

        @Override
        public String getETag() {
            return eTag;
        }

        @Override
        public long getSize() {
            return contentLength;
        }

        @Override
        public Instant getLastModified() {
            return lastModified;
        }

        @Override
        public void delete(final boolean ignore) {
            bucket.deleteObject(getAbsoluteName());
            node.compareAndSet(this, new NewObject());
        }

        @Override
        public ObjectMetadata getObjectMetadata() {
            return new ObjectMetadata(eTag, contentLength, lastModified, contentType, userMetadata);
        }

        @Override
        public Upload upload(final UploadRequest request) {
            return uploadAndReplace(this, request);
        }

        @Override
        public FileDownload download(final DownloadFileRequest request) {
            return bucket.getClient().getTransferManager().downloadFile(request);
        }
    }

    /**
     * When listing Objects in S3 via the AmazonS3 API you will
     * get several S3Object instances which have similar
     * data as a full object response but of course in different places.
     */
    private class ObjectSummary implements Node {
        private final S3Object summary;

        public ObjectSummary(final S3Object summary) {
            this.summary = summary;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public boolean isFile() {
            return true;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public S3File getFile(final String name) {
            final String message = String.format("S3File '%s' is a not directory and cannot have child '%s'", path.getAbsoluteName(), name);
            throw new UnsupportedOperationException(message);
        }

        @Override
        public InputStream getValueAsStream() {
            return openStreamAndReplace(this);
        }

        @Override
        public String getValueAsString() {
            return readAndReplace(this);
        }

        @Override
        public void setValueAsStream(final InputStream inputStream) {
            writeStreamAndReplace(this, inputStream);
        }

        @Override
        public void setValueAsString(final String value) {
            writeStringAndReplace(this, value);
        }

        @Override
        public void setValueAsFile(final File value) {
            writeFileAndReplace(this, value);
        }

        @Override
        public String getETag() {
            return stripQuotes(summary.eTag());
        }

        @Override
        public long getSize() {
            return summary.size();
        }

        @Override
        public Instant getLastModified() {
            return summary.lastModified();
        }

        @Override
        public void delete(final boolean ignore) {
            bucket.deleteObject(summary.key());
            node.compareAndSet(this, new NewObject());
        }

        @Override
        public ObjectMetadata getObjectMetadata() {
            return ObjectMetadata.fromListing(summary);
        }

        @Override
        public Upload upload(final UploadRequest request) {
            return uploadAndReplace(this, request);
        }

        @Override
        public FileDownload download(final DownloadFileRequest request) {
            return bucket.getClient().getTransferManager().downloadFile(request);
        }

    }

    /**
     * Any time an Object in S3 is updated we will have new last modified times,
     * new file size and new metadata.  When this happens we replace the existing
     * Node with a new one that represents the updated data.
     */
    private class UpdatedObject implements Node {
        private final PutObjectResponse result;
        private final long contentLength;

        public UpdatedObject(final PutObjectResponse result, final long contentLength) {
            this.result = result;
            this.contentLength = contentLength;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public boolean isFile() {
            return true;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public S3File getFile(final String name) {
            final String message = String.format("S3File '%s' is a not directory and cannot have child '%s'", path.getAbsoluteName(), name);
            throw new UnsupportedOperationException(message);
        }

        @Override
        public InputStream getValueAsStream() {
            return openStreamAndReplace(this);
        }

        @Override
        public String getValueAsString() {
            return readAndReplace(this);
        }

        @Override
        public void setValueAsStream(final InputStream inputStream) {
            writeStreamAndReplace(this, inputStream);
        }

        @Override
        public void setValueAsString(final String value) {
            writeStringAndReplace(this, value);
        }

        @Override
        public void setValueAsFile(final File value) {
            writeFileAndReplace(this, value);
        }

        @Override
        public String getETag() {
            return stripQuotes(result.eTag());
        }

        @Override
        public long getSize() {
            return contentLength;
        }

        @Override
        public Instant getLastModified() {
            return resolve(this).getLastModified();
        }

        @Override
        public void delete(final boolean ignore) {
            bucket.deleteObject(path.getAbsoluteName());
            node.compareAndSet(this, new NewObject());
        }

        @Override
        public ObjectMetadata getObjectMetadata() {
            return ObjectMetadata.fromPut(result, contentLength);
        }

        @Override
        public Upload upload(final UploadRequest request) {
            return uploadAndReplace(this, request);
        }

        @Override
        public FileDownload download(final DownloadFileRequest request) {
            return bucket.getClient().getTransferManager().downloadFile(request);
        }
    }

    /**
     * Represents an object that is being uploaded.
     */
    private class UploadingObject implements Node {

        public UploadingObject() {
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public boolean isFile() {
            return true;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public S3File getFile(final String name) {
            final String message = String.format("S3File '%s' is a not directory and cannot have child '%s'", path.getAbsoluteName(), name);
            throw new UnsupportedOperationException(message);
        }

        @Override
        public InputStream getValueAsStream() {
            return openStreamAndReplace(this);
        }

        @Override
        public String getValueAsString() {
            return readAndReplace(this);
        }

        @Override
        public void setValueAsStream(final InputStream inputStream) {
            writeStreamAndReplace(this, inputStream);
        }

        @Override
        public void setValueAsString(final String value) {
            writeStringAndReplace(this, value);
        }

        @Override
        public void setValueAsFile(final File value) {
            writeFileAndReplace(this, value);
        }

        @Override
        public String getETag() {
            return resolve(this).getETag();
        }

        @Override
        public long getSize() {
            return resolve(this).getSize();
        }

        @Override
        public Instant getLastModified() {
            return resolve(this).getLastModified();
        }

        @Override
        public void delete(final boolean ignore) {
            bucket.deleteObject(path.getAbsoluteName());
            node.compareAndSet(this, new NewObject());
        }

        @Override
        public ObjectMetadata getObjectMetadata() {
            return resolve(this).getObjectMetadata();
        }

        @Override
        public Upload upload(final UploadRequest request) {
            return uploadAndReplace(this, request);
        }

        @Override
        public FileDownload download(final DownloadFileRequest request) {
            return bucket.getClient().getTransferManager().downloadFile(request);
        }
    }

    /**
     * Represents an object that may not yet have been created and
     * therefore has no metadata.  It may be a file, it may imply
     * a directory, it may not exist, we don't know.
     * <p>
     * Once written to S3 the node will be replaced with a node that has
     * all the metadata and can be both read and written.
     */
    private class Unknown implements Node {

        public Unknown() {
        }

        @Override
        public boolean exists() {
            return resolve(this).exists();
        }

        @Override
        public boolean isFile() {
            return resolve(this).isFile();
        }

        @Override
        public boolean isDirectory() {
            return resolve(this).isDirectory();
        }

        @Override
        public Stream<S3File> files() {
            return bucket.objects(ListObjectsRequest.builder().prefix(path.getSearchPrefix()).build());
        }

        @Override
        public Stream<S3File> files(final ListObjectsRequest request) {
            return listRequest(request);
        }

        @Override
        public Stream<S3File> walk(final int maxDepth, final String delimiter) {
            return performWalk(maxDepth, delimiter);
        }

        @Override
        public Stream<S3File> walk(final ListObjectsRequest request, final int maxDepth, final String delimiter) {
            return performWalk(request, maxDepth, delimiter);
        }

        @Override
        public Stream<S3File> list() {
            return performSingleLevelListing();
        }

        @Override
        public S3File getFile(final String name) {
            final Path child = path.getChild(name);
            return new S3File(bucket, child, Unknown.class);
        }

        @Override
        public InputStream getValueAsStream() {
            return openStreamAndReplace(this);
        }

        @Override
        public String getValueAsString() {
            return readAndReplace(this);
        }

        @Override
        public void setValueAsStream(final InputStream inputStream) {
            writeStreamAndReplace(this, inputStream);
        }

        @Override
        public void setValueAsString(final String value) {
            writeStringAndReplace(this, value);
        }

        @Override
        public void setValueAsFile(final File value) {
            writeFileAndReplace(this, value);
        }

        @Override
        public String getETag() {
            return resolve(this).getETag();
        }

        @Override
        public long getSize() {
            return resolve(this).getSize();
        }

        @Override
        public Instant getLastModified() {
            return resolve(this).getLastModified();
        }

        @Override
        public void delete(final boolean force) {
            resolve(this).delete(force);
        }

        @Override
        public ObjectMetadata getObjectMetadata() {
            return resolve(this).getObjectMetadata();
        }

        @Override
        public Upload upload(final UploadRequest request) {
            return uploadAndReplace(this, request);
        }

        @Override
        public FileDownload download(final DownloadFileRequest request) {
            return resolve(this).download(request);
        }

    }

    /**
     * Represents an object that has not yet have been created and
     * therefore has no metadata.  It may be written, but not read.
     * <p>
     * Attempts to resolve an S3Object that fail will result in
     * this object being set as the node.
     * <p>
     * Once written to S3 the node will be replaced with a node that has
     * all the metadata and can be both read and written.
     */
    private class NewObject implements Node {

        public NewObject() {
        }

        @Override
        public boolean exists() {
            return false;
        }

        @Override
        public boolean isFile() {
            return false;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public S3File getFile(final String name) {
            final Path child = path.getChild(name);
            return new S3File(bucket, child, Unknown.class);
        }

        @Override
        public InputStream getValueAsStream() {
            throw new NoSuchS3ObjectException(getBucketName(), getAbsoluteName());
        }

        @Override
        public String getValueAsString() {
            throw new NoSuchS3ObjectException(getBucketName(), getAbsoluteName());
        }

        @Override
        public void setValueAsStream(final InputStream inputStream) {
            writeStreamAndReplace(this, inputStream);
        }

        @Override
        public void setValueAsString(final String value) {
            writeStringAndReplace(this, value);
        }

        @Override
        public void setValueAsFile(final File value) {
            writeFileAndReplace(this, value);
        }

        @Override
        public String getETag() {
            throw new NoSuchS3ObjectException(getBucketName(), getAbsoluteName());
        }

        @Override
        public long getSize() {
            throw new NoSuchS3ObjectException(getBucketName(), getAbsoluteName());
        }

        @Override
        public Instant getLastModified() {
            throw new NoSuchS3ObjectException(getBucketName(), getAbsoluteName());
        }

        @Override
        public void delete(final boolean ignore) {
            throw new NoSuchS3ObjectException(getBucketName(), getAbsoluteName());
        }

        @Override
        public ObjectMetadata getObjectMetadata() {
            throw new NoSuchS3ObjectException(getBucketName(), getAbsoluteName());
        }

        @Override
        public Upload upload(final UploadRequest request) {
            return uploadAndReplace(this, request);
        }

        @Override
        public FileDownload download(final DownloadFileRequest request) {
            throw new NoSuchS3ObjectException(getBucketName(), getAbsoluteName());
        }
    }

    private void writeStringAndReplace(final Node current, final String value) {
        final PutObjectResponse result = bucket.setObjectAsString(path.getAbsoluteName(), value);
        node.compareAndSet(current, new UpdatedObject(result, 0));
    }

    private void writeFileAndReplace(final Node current, final File value) {
        final PutObjectResponse result = bucket.setObjectAsFile(path.getAbsoluteName(), value);
        node.compareAndSet(current, new UpdatedObject(result, 0));
    }

    private void writeStreamAndReplace(final Node current, final InputStream inputStream) {
        final PutObjectResponse result = bucket.setObjectAsStream(path.getAbsoluteName(), inputStream);
        node.compareAndSet(current, new UpdatedObject(result, 0));
    }

    private Upload uploadAndReplace(final Node current, final UploadRequest request) {
        try {
            return bucket.getClient().getTransferManager().upload(request);
        } finally {
            node.compareAndSet(current, new UploadingObject());
        }
    }

    /**
     * Reading the object also gives us refreshed metadata, so we should always
     * replace the current node with a new Metadata instance.
     * <p>
     * Critical note, calling this method and not closing the InputStream will
     * cause a connection leak that will eventually prevent further S3 calls
     * of any kind.
     */
    private InputStream openStreamAndReplace(final Node current) {
        final ResponseInputStream<GetObjectResponse> responseStream;
        try {
            responseStream = bucket.getObject(path.getAbsoluteName());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new NoSuchS3ObjectException(bucket.getName(), path.getAbsoluteName(), e);
            }
            throw e;
        }
        final GetObjectResponse response = responseStream.response();
        final Metadata metadata = new Metadata(response.eTag(), response.contentLength(), response.lastModified(), response.contentType(), response.metadata());
        node.compareAndSet(current, metadata);
        return responseStream;
    }

    private String readAndReplace(final Node current) {
        try {
            try (final InputStream in = openStreamAndReplace(current)) {
                return IO.slurp(in);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read content for " + path.getAbsoluteName(), e);
        }
    }

    private Stream<S3File> performWalk(final int depth, final String delimiter) {
        return asStream(new WalkingIterator(this, depth, delimiter));
    }

    private Stream<S3File> performWalk(final ListObjectsRequest request, final int depth, final String delimiter) {
        return asStream(new WalkingIterator(request, this, depth, delimiter));
    }

    private Stream<S3File> performSingleLevelListing() {
        return asStream(new SingleLevelIterator(this));
    }

    private Node resolve(final Node current) {
        final HeadObjectResponse response;

        try {
            final String absoluteName = path.getAbsoluteName();
            response = bucket.getObjectMetadata(absoluteName);
        } catch (final S3Exception e) {
            if (e.statusCode() == 404) {
                final NewObject newObject = new NewObject();
                if (node.compareAndSet(current, newObject)) {
                    return newObject;
                } else {
                    return node.get();
                }
            }
            throw e;
        }


        final Metadata newNode = new Metadata(response.eTag(), response.contentLength(), response.lastModified(), response.contentType(), response.metadata());

        if (node.compareAndSet(current, newNode)) {
            return newNode;
        } else {
            return node.get();
        }
    }

    private Stream<S3File> listRequest(final ListObjectsRequest request) {
        Objects.requireNonNull(request);
        if (request.prefix() == null) {
            return bucket.objects(request.toBuilder().prefix(path.getSearchPrefix()).build());
        } else {
            return bucket.objects(request);
        }
    }

    @Override
    public String toString() {
        return "S3File{" +
                "bucket='" + bucket.getName() +
                "', path='" + path.getAbsoluteName() +
                "', node='" + node.get().getClass().getSimpleName() +
                "'}";
    }

    class WalkingIterator implements Iterator<S3File> {

        static final int INFINITE = Integer.MAX_VALUE;
        private final int remaining;
        private Iterator<S3File> iterator;
        private final List<Iterator<S3File>> children = new ArrayList<>();

        private final ListObjectsRequest request;

        public WalkingIterator(final S3File file, final int depth, final String delimiter) {
            this(ListObjectsRequest.builder().build(), file, depth, delimiter);
        }

        public WalkingIterator(final ListObjectsRequest request, final S3File file, final int depth, final String delimiter) {
            final ListObjectsRequest.Builder builder = request.toBuilder()
                    .delimiter(delimiter)
                    .bucket(bucket.getName());

            if (request.prefix() == null) {
                builder.prefix(file.getPath().getSearchPrefix());
            }

            this.request = builder.build();

            iterator = new Listing(S3Client.join(getS3().listObjects(this.request)));
            remaining = depth == INFINITE ? INFINITE : depth - 1;
        }

        class Listing implements Iterator<S3File> {

            private final ListObjectsResponse response;
            private final Iterator<S3File> objectListingIterator;

            public Listing(final ListObjectsResponse response) {
                this.response = response;
                this.objectListingIterator = iterator(response);
            }

            private Iterator<S3File> iterator(final ListObjectsResponse response) {
                return new IteratorIterator<>(
                        new ObjectSummaryIterator(response.contents().iterator()),
                        new DirectoryIterator(response.commonPrefixes().stream()
                                .map(CommonPrefix::prefix).iterator())
                );
            }

            @Override
            public boolean hasNext() {
                /*
                 * Drain out anything from the current response
                 */
                if (objectListingIterator.hasNext()) {
                    return true;
                }

                /*
                 * Replace this iterator with one for the next response
                 */
                if (response.isTruncated()) {
                    String marker = response.nextMarker();
                    if (marker == null) {
                        final java.util.List<S3Object> contents = response.contents();
                        if (!contents.isEmpty()) {
                            marker = contents.get(contents.size() - 1).key();
                        }
                    }
                    final ListObjectsRequest nextRequest = request.toBuilder().marker(marker).build();
                    iterator = new Listing(S3Client.join(getS3().listObjects(nextRequest)));
                    return iterator.hasNext();
                }

                /*
                 * If we're done with all responses, now descend into the
                 * children if there are any.
                 *
                 * Replace this iterator with one for the children
                 */
                if (children.size() > 0) {
                    iterator = new IteratorIterator<>(children);
                    return iterator.hasNext();
                }

                return false;
            }

            @Override
            public S3File next() {
                final S3File next = objectListingIterator.next();

                if (next.isDirectory() && (remaining == INFINITE || remaining > 0)) {
                    children.add(new WalkingIterator(request.toBuilder().prefix(null).build(), next, remaining, request.delimiter()));
                }

                return next;
            }
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public S3File next() {
            return iterator.next();
        }

        private S3AsyncClient getS3() {
            return bucket.getClient().getS3();
        }
    }

    class SingleLevelIterator implements Iterator<S3File> {

        private Iterator<S3File> iterator;
        private ListObjectsResponse response;
        private ListObjectsRequest request;

        public SingleLevelIterator(final S3File file) {
            this.request = ListObjectsRequest.builder()
                    .delimiter("/")
                    .prefix(file.getPath().getSearchPrefix())
                    .bucket(bucket.getName())
                    .build();

            this.response = S3Client.join(bucket.getClient().getS3().listObjects(this.request));
            this.iterator = iteratorForResponse(this.response);
        }

        private Iterator<S3File> iteratorForResponse(final ListObjectsResponse response) {
            return new IteratorIterator<>(
                    new ObjectSummaryIterator(response.contents().iterator()),
                    new DirectoryIterator(response.commonPrefixes().stream()
                            .map(CommonPrefix::prefix).iterator())
            );
        }

        @Override
        public boolean hasNext() {
            if (iterator.hasNext()) return true;
            if (!response.isTruncated()) return false;

            String marker = response.nextMarker();
            if (marker == null) {
                final java.util.List<S3Object> contents = response.contents();
                if (!contents.isEmpty()) {
                    marker = contents.get(contents.size() - 1).key();
                }
            }
            request = request.toBuilder().marker(marker).build();
            response = S3Client.join(bucket.getClient().getS3().listObjects(request));
            iterator = iteratorForResponse(response);

            return hasNext();
        }

        @Override
        public S3File next() {
            return iterator.next();
        }
    }

    class DirectoryIterator implements Iterator<S3File> {
        private final Iterator<String> iterator;

        public DirectoryIterator(final Iterator<String> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public S3File next() {
            return new S3File(bucket, Path.fromKey(iterator.next()), Directory.class);
        }
    }

    class ObjectSummaryIterator implements Iterator<S3File> {
        private final Iterator<S3Object> iterator;

        public ObjectSummaryIterator(final Iterator<S3Object> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public S3File next() {
            return new S3File(bucket, iterator.next());
        }
    }

}
