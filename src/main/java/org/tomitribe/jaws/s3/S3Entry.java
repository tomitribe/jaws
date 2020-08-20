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
import com.amazonaws.services.s3.model.Owner;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import java.util.Date;

public class S3Entry {
    private final S3Client client;
    private final S3ObjectSummary summary;
    private final Path path;

    public S3Entry(final S3Client client, final S3ObjectSummary summary) {
        this.client = client;
        this.summary = summary;
        this.path = Path.fromKey(summary.getKey());
    }

    public S3Object getValue() throws SdkClientException {
        return client.getS3().getObject(summary.getBucketName(), summary.getKey());
    }

    public S3ObjectInputStream getValueAsStream() throws SdkClientException {
        final S3Object object = client.getS3().getObject(summary.getBucketName(), summary.getKey());
        if (object == null) throw new NoSuchS3ObjectException(summary.getBucketName(), summary.getKey());
        return object.getObjectContent();
    }

    public String getValueAsString() throws AmazonServiceException, SdkClientException {
        return client.getS3().getObjectAsString(summary.getBucketName(), summary.getKey());
    }

    public String getBucketName() {
        return summary.getBucketName();
    }

    public void setBucketName(final String bucketName) {
        summary.setBucketName(bucketName);
    }

    public String getName() {
        return path.getName();
    }

    public String getKey() {
        return summary.getKey();
    }

    public void setKey(final String key) {
        summary.setKey(key);
    }

    public String getETag() {
        return summary.getETag();
    }

    public void setETag(final String eTag) {
        summary.setETag(eTag);
    }

    public long getSize() {
        return summary.getSize();
    }

    public void setSize(final long size) {
        summary.setSize(size);
    }

    public Date getLastModified() {
        return summary.getLastModified();
    }

    public void setLastModified(final Date lastModified) {
        summary.setLastModified(lastModified);
    }

    public Owner getOwner() {
        return summary.getOwner();
    }

    public void setOwner(final Owner owner) {
        summary.setOwner(owner);
    }

    public String getStorageClass() {
        return summary.getStorageClass();
    }

    public void setStorageClass(final String storageClass) {
        summary.setStorageClass(storageClass);
    }
}
