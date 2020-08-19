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
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

import java.io.File;
import java.io.InputStream;
import java.util.Objects;
import java.util.stream.Stream;

public class S3Directory {

    private final S3Bucket bucket;

    /**
     * Similar to File.getParentFile()
     */
    private final S3Directory parent;

    /**
     * Similar to File.getAbsolutePath()
     *
     * The prefix does contain the `name` and does end with a `/`
     */
    private final String prefix;

    /**
     * Similar to File.getName()
     */
    private final String name;

    S3Directory(final S3Bucket bucket) {
        this.bucket = bucket;
        this.parent = null;
        this.prefix = "";
        this.name = "";
    }

    S3Directory(final S3Bucket bucket, final S3Directory parent, final String name) {
        this.bucket = bucket;
        this.parent = parent;
        this.prefix = parent == null ? name + "/" : parent.prefix + name + "/";
        this.name = name;
    }

    public S3Bucket getBucket() {
        return bucket;
    }

    public S3Directory getDirectory(final String name) {
        return new S3Directory(bucket, this, name);
    }

    public S3Directory getParentDirectory() {
        return parent;
    }

    public Stream<S3Entry> objects() {
        return bucket.objects(new ListObjectsRequest().withPrefix(prefix));
    }

    public Stream<S3Entry> objects(final ListObjectsRequest request) {
        Objects.requireNonNull(request);
        return bucket.objects(request.withPrefix(prefix));
    }

    public PutObjectResult putObject(final String key, final File file) throws SdkClientException, AmazonServiceException {
        return bucket.putObject(prefix + key, file);
    }

    public PutObjectResult putObject(final String key, final InputStream inputStream, final ObjectMetadata objectMetadata) throws SdkClientException, AmazonServiceException {
        return bucket.putObject(prefix + key, inputStream, objectMetadata);
    }

    public PutObjectResult putObject(final String key, final String s2) throws AmazonServiceException, SdkClientException {
        return bucket.putObject(prefix + key, s2);
    }

    public S3Object getObject(final String key) throws SdkClientException, AmazonServiceException {
        return bucket.getObject(prefix + key);
    }

    public S3ObjectInputStream getObjectAsStream(final String key) throws SdkClientException, AmazonServiceException {
        return bucket.getObjectAsStream(prefix + key);
    }

    public String getObjectAsString(final String key) throws AmazonServiceException, SdkClientException {
        return bucket.getObjectAsString(prefix + key);
    }

    /**
     * The name of the directory, excluding parent directories
     *
     * Similar to java.io.File.getName()
     */
    public String getName() {
        return name;
    }

    /**
     * The full name of the key relative to the bucket
     *
     * Similar to java.io.File.getAbsolutePath()
     */
    public String getAbsoluteName() {
        return prefix;
    }
}
