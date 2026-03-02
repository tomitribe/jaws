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

import software.amazon.awssdk.transfer.s3.model.Upload;
import software.amazon.awssdk.transfer.s3.progress.TransferListener;

import java.io.InputStream;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * Base interface for strongly-typed S3 proxies. User-defined interfaces
 * extend {@code S3}, {@link Dir}, or {@link File} and are instantiated
 * via {@link S3File#as(Class)} or {@link S3Bucket#as(Class)}.
 *
 * <p>Methods declared on user interfaces are dispatched by the proxy
 * handler based on return type:
 * <ul>
 *   <li>A no-arg method returning an interface wraps the child S3File
 *       as a proxy of that type</li>
 *   <li>A method taking a {@code String} and returning an interface
 *       looks up the named child and wraps it</li>
 *   <li>{@code Stream}, {@code List}, {@code Set}, {@code Collection},
 *       and array return types produce listings filtered and mapped
 *       according to the element type</li>
 * </ul>
 *
 * <p>The {@link Filter @Filter} annotation may be placed on methods
 * or on the element type interface to apply client-side predicates.
 * Interface-level filters are applied before method-level filters.
 *
 * @see Dir
 * @see File
 * @see S3File#as(Class)
 * @see S3Bucket#as(Class)
 */
public interface S3 {

    /**
     * Returns the underlying {@link S3File} backing this proxy.
     *
     * @return the S3File for this proxy's location in the bucket
     */
    S3File file();

    /**
     * Returns the parent {@link S3File} of this proxy's location.
     *
     * @return the parent S3File, or {@code null} if this is the bucket root
     */
    S3File parent();

    /**
     * Represents an S3 directory (prefix). User interfaces that model
     * a folder or container of S3 objects should extend this interface
     * to gain access to listing and child-lookup methods.
     *
     * <p><b>Return-type dispatch:</b> when a proxy method returns
     * {@code Stream<X>} (or {@code List<X>}, {@code Set<X>}, array, etc.)
     * where {@code X extends S3.Dir}, the library automatically uses a
     * delimiter-based listing and returns only directories (commonPrefixes)
     * at one level.
     *
     * <h3>Example</h3>
     * <pre>{@code
     * public interface Catalog extends S3.Dir {
     *     Stream<Category> categories();    // only directories
     *     Category category(String name);   // named child directory
     * }
     *
     * Catalog catalog = bucket.as(Catalog.class);
     * catalog.categories().forEach(c -> System.out.println(c.file().getName()));
     * }</pre>
     */
    interface Dir extends S3 {

        /**
         * Returns the child {@link S3File} with the given name.
         * The child may or may not exist in S3.
         *
         * @param name the child object key relative to this directory
         * @return the child S3File
         */
        S3File file(String name);

        /**
         * Returns all objects under this directory recursively
         * as a flat listing (no delimiter).
         *
         * @return a stream of all descendant S3Files
         */
        Stream<S3File> files();

        /**
         * Returns both immediate child files and directories (one level deep).
         * Uses a delimiter-based listing, returning both contents and
         * commonPrefixes.
         *
         * @return a stream of immediate child S3Files
         */
        Stream<S3File> list();

        /**
         * Uploads a local file into this directory using the file's name
         * as the object key. Content type and metadata are derived
         * automatically from the file.
         *
         * <p>Equivalent to {@code file(file.getName()).upload(file)}.
         *
         * @param file the local file to upload
         * @return the in-progress Upload
         */
        default Upload upload(final java.io.File file) {
            return file(file.getName()).upload(file);
        }

        /**
         * Uploads a local file into this directory using the file's name
         * as the object key, with a transfer listener for progress tracking.
         *
         * <p>Equivalent to {@code file(file.getName()).upload(file, listener)}.
         *
         * @param file     the local file to upload
         * @param listener the listener for transfer progress events, or {@code null}
         * @return the in-progress Upload
         */
        default Upload upload(final java.io.File file, final TransferListener listener) {
            return file(file.getName()).upload(file, listener);
        }
    }

    /**
     * Represents an S3 object with content. User interfaces that model
     * a single S3 object and need to read/write its content or inspect
     * its metadata should extend this interface.
     *
     * <p>All default methods delegate to the underlying {@link S3File}
     * obtained via {@link #file()}.
     *
     * <p><b>Return-type dispatch:</b> when a proxy method returns
     * {@code Stream<X>} (or {@code List<X>}, {@code Set<X>}, array, etc.)
     * where {@code X extends S3.File}, the library automatically uses a
     * delimiter-based listing and returns only files (contents, not
     * commonPrefixes) at one level.
     *
     * <h3>Example</h3>
     * <pre>{@code
     * public interface Config extends S3.File {
     *     // inherits getValueAsString(), getSize(), etc.
     * }
     *
     * Config config = bucket.getFile("app.properties").as(Config.class);
     * String content = config.getValueAsString();
     * }</pre>
     */
    interface File extends S3 {

        /**
         * Returns the object content as an {@link InputStream}.
         * The caller is responsible for closing the stream.
         *
         * @return an input stream of the object content
         */
        default InputStream getValueAsStream() {
            return file().getValueAsStream();
        }

        /**
         * Returns the object content as a {@link String}.
         *
         * @return the object content
         */
        default String getValueAsString() {
            return file().getValueAsString();
        }

        /**
         * Replaces the object content by reading from the given input stream.
         *
         * @param is the input stream to read from
         */
        default void setValueAsStream(InputStream is) {
            file().setValueAsStream(is);
        }

        /**
         * Replaces the object content with the given string.
         *
         * @param value the new content
         */
        default void setValueAsString(String value) {
            file().setValueAsString(value);
        }

        /**
         * Replaces the object content with the contents of the given file.
         *
         * @param file the local file whose content will be uploaded
         */
        default void setValueAsFile(java.io.File file) {
            file().setValueAsFile(file);
        }

        /**
         * Returns the ETag of the object, typically an MD5 hash of the content.
         *
         * @return the ETag, or {@code null} if not yet fetched
         */
        default String getETag() {
            return file().getETag();
        }

        /**
         * Returns the size of the object in bytes.
         *
         * @return the content length in bytes
         */
        default long getSize() {
            return file().getSize();
        }

        /**
         * Returns the last modified timestamp of the object.
         *
         * @return the last modified instant, or {@code null} if not available
         */
        default Instant getLastModified() {
            return file().getLastModified();
        }

        /**
         * Returns the full {@link ObjectMetadata} for this object,
         * including content type, user metadata, and other attributes.
         *
         * @return the object metadata
         */
        default ObjectMetadata getObjectMetadata() {
            return file().getObjectMetadata();
        }
    }

}
