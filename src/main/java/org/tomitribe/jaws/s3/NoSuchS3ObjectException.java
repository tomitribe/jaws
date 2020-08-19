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

public class NoSuchS3ObjectException extends IllegalStateException {

    private final String bucketName;
    private final String key;

    public NoSuchS3ObjectException(final String bucketName, final String key) {
        super(String.format("Key '%s' not found in bucket '%s'", key, bucketName));
        this.bucketName = bucketName;
        this.key = key;
    }

    public NoSuchS3ObjectException(final String bucketName, final String key, final Throwable throwable) {
        super(String.format("Key '%s' not found in bucket '%s'", key, bucketName), throwable);
        this.bucketName = bucketName;
        this.key = key;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getKey() {
        return key;
    }
}
