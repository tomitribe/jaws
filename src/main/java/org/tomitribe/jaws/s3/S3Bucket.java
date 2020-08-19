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
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.Owner;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import java.io.File;
import java.io.InputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.stream.Stream;

public class S3Bucket {
    private final S3Client client;
    private final Bucket bucket;
    private final AmazonS3 s3;

    public S3Bucket(final S3Client client, final Bucket bucket) {
        this.client = client;
        this.bucket = bucket;
        s3 = client.getS3();
    }

    public S3Directory asDirectory() {
        return new S3Directory(this);
    }

    public Stream<S3Entry> objects() {
        final ObjectListingIterator iterator = new ObjectListingIterator(bucket.getName());
        return S3Client.asStream(iterator)
                .map(s3ObjectSummary -> new S3Entry(client, s3ObjectSummary))
                ;
    }

    public Stream<S3Entry> objects(final ListObjectsRequest request) {
        request.setBucketName(bucket.getName());
        final ObjectListingIterator iterator = new ObjectListingIterator(request);
        return S3Client.asStream(iterator)
                .map(s3ObjectSummary -> new S3Entry(client, s3ObjectSummary))
                ;
    }

    public PutObjectResult putObject(final String key, final File file) throws SdkClientException, AmazonServiceException {
        return s3.putObject(bucket.getName(), key, file);
    }

    public PutObjectResult putObject(final String key, final InputStream inputStream, final ObjectMetadata objectMetadata) throws SdkClientException, AmazonServiceException {
        return s3.putObject(bucket.getName(), key, inputStream, objectMetadata);
    }

    public PutObjectResult putObject(final String key, final String s2) throws AmazonServiceException, SdkClientException {
        return s3.putObject(bucket.getName(), key, s2);
    }

    public S3Object getObject(final String key) throws SdkClientException, AmazonServiceException {
        return s3.getObject(bucket.getName(), key);
    }

    public S3ObjectInputStream getObjectAsStream(final String key) throws SdkClientException, AmazonServiceException {
        try {
            final S3Object object = s3.getObject(bucket.getName(), key);
            if (object == null) throw new NoSuchS3ObjectException(bucket.getName(), key);
            return object.getObjectContent();
        } catch (AmazonS3Exception e) {
            if (e.getMessage().contains("The specified key does not exist")) {
                throw new NoSuchS3ObjectException(bucket.getName(), key, e);
            }
            throw e;
        }
    }

    public String getObjectAsString(final String key) throws AmazonServiceException, SdkClientException {
        try {
            return s3.getObjectAsString(bucket.getName(), key);
        } catch (AmazonS3Exception e) {
            if (e.getMessage().contains("The specified key does not exist")) {
                throw new NoSuchS3ObjectException(bucket.getName(), key, e);
            }
            throw e;
        }
    }

    public Owner getOwner() {
        return bucket.getOwner();
    }

    public void setOwner(final Owner owner) {
        bucket.setOwner(owner);
    }

    public Date getCreationDate() {
        return bucket.getCreationDate();
    }

    public void setCreationDate(final Date creationDate) {
        bucket.setCreationDate(creationDate);
    }

    public String getName() {
        return bucket.getName();
    }

    public void setName(final String name) {
        bucket.setName(name);
    }

    class ObjectListingIterator implements Iterator<S3ObjectSummary> {

        private Iterator<S3ObjectSummary> iterator;
        private ObjectListing objectListing;

        public ObjectListingIterator(final String bucketName) {
            objectListing = client.getS3().listObjects(bucketName);
            iterator = objectListing.getObjectSummaries().iterator();
        }

        public ObjectListingIterator(final ListObjectsRequest bucketName) {
            objectListing = client.getS3().listObjects(bucketName);
            iterator = objectListing.getObjectSummaries().iterator();
        }

        @Override
        public boolean hasNext() {
            if (iterator.hasNext()) return true;
            if (!objectListing.isTruncated()) return false;

            objectListing = client.getS3().listNextBatchOfObjects(objectListing);
            iterator = objectListing.getObjectSummaries().iterator();

            return hasNext();
        }

        @Override
        public S3ObjectSummary next() {
            return iterator.next();
        }
    }
}
