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

import java.io.InputStream;
import java.time.Instant;
import java.util.stream.Stream;

public interface S3 {

    S3File file();

    S3File parent();

    /**
     * Represents an S3 directory. User interfaces that need
     * {@code file(String)}, {@code files()}, or {@code list()}
     * should extend this interface.
     *
     * <p>When a proxy method returns {@code Stream<X>} where
     * {@code X extends S3.Dir}, the library automatically uses
     * a delimiter-based listing and returns only directories
     * (commonPrefixes) at one level.
     */
    interface Dir extends S3 {

        /**
         * Returns the child S3File with the given name.
         */
        S3File file(String name);

        /**
         * Returns all objects under this directory recursively
         * (flat listing, no delimiter).
         */
        Stream<S3File> files();

        /**
         * Returns both immediate child files and directories (one level deep).
         * Uses a delimiter-based listing, returning both contents and commonPrefixes.
         */
        Stream<S3File> list();
    }

    /**
     * Represents an S3 file (object). User interfaces that need
     * content access or metadata should extend this interface.
     *
     * <p>When a proxy method returns {@code Stream<X>} where
     * {@code X extends S3.File}, the library automatically uses
     * a delimiter-based listing and returns only files
     * (contents) at one level.
     */
    interface File extends S3 {

        default InputStream getValueAsStream() {
            return file().getValueAsStream();
        }

        default String getValueAsString() {
            return file().getValueAsString();
        }

        default void setValueAsStream(InputStream is) {
            file().setValueAsStream(is);
        }

        default void setValueAsString(String value) {
            file().setValueAsString(value);
        }

        default void setValueAsFile(java.io.File file) {
            file().setValueAsFile(file);
        }

        default String getETag() {
            return file().getETag();
        }

        default long getSize() {
            return file().getSize();
        }

        default Instant getLastModified() {
            return file().getLastModified();
        }

        default ObjectMetadata getObjectMetadata() {
            return file().getObjectMetadata();
        }
    }

}
