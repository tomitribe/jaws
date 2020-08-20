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

import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

/**
 * S3File aims to provide an abstraction over the Amazon S3 API
 * that acts like java.io.File and provides a stable reference
 * to all the states of an S3 object.
 *
 * Like java.io.File, S3File can refer to an S3 object before it
 * exists, once it is created, after it is updated and after it
 * is deleted.  Throughout these various states, callers may simply
 * hold the same S3File instance and expect it will reflect the
 * most current information that has been fetched.
 */
public class S3File {

    private final S3Bucket bucket;
    private final Path path;
    private final AtomicReference<Node> node = new AtomicReference<>();

    private S3File(final S3Bucket bucket, final S3ObjectSummary summary) {
        this.bucket = bucket;
        this.path = Path.fromKey(summary.getKey());
        this.node.set(new ObjectSummary(summary));
    }

    private S3File(final S3Bucket bucket, final S3Object object) {
        this.bucket = bucket;
        this.path = Path.fromKey(object.getKey());
        this.node.set(new Object(object));
    }

    private S3File(final S3Bucket bucket, final Path path) {
        this.bucket = bucket;
        this.path = path;
        this.node.set(new Directory());
    }

    private S3File(final S3Bucket bucket, final Path path, final Node node) {
        this.bucket = bucket;
        this.path = path;
        this.node.set(node);
    }

    public boolean exists() {
        return node.get().exists();
    }

    public boolean isFile() {
        return node.get().isFile();
    }

    public boolean isDirectory() {
        return node.get().isDirectory();
    }

    public S3File getParentFile() {
        final Path parent = path.getParent();
        if (parent == null) return null;
        return new S3File(bucket, parent);
    }

    public S3File getChildFile(final String name) {
        return node.get().getChildFile(name);
    }

    public String getAbsoluteName() {
        return path.getAbsoluteName();
    }

    public String getName() {
        return path.getName();
    }

    public S3Bucket getBucket() {
        return bucket;
    }

    public S3ObjectInputStream getValueAsStream() {
        return node.get().getValueAsStream();
    }

    public String getValueAsString() {
        return node.get().getValueAsString();
    }

    public S3OutputStream setValueAsStream() {
        return node.get().setValueAsStream();
    }

    public void setValueAsString(final String value) {
        node.get().setValueAsString(value);
    }

    public String getBucketName() {
        return bucket.getName();
    }

    public String getETag() {
        return node.get().getETag();
    }

    public long getSize() {
        return node.get().getSize();
    }

    public Date getLastModified() {
        return node.get().getLastModified();
    }

    /**
     * AmazonS3 API has a very large number of ways to get at
     * the same data.
     *
     * For example if you look up a single object in S3 you will
     * get an S3Object instance.  If you list objects in S3 you
     * will get several S3ObjectSummary instances.  If you update
     * an object in S3 you will get a PutObjectResult.
     *
     * This interface serves as a way to adapt them all to one
     * common interface, making all these variations manageable.
     *
     * The name 'Node' is somewhat inspired by the inode concept
     * of the unix file system.
     */
    private interface Node {

        boolean isFile();

        boolean isDirectory();

        boolean exists();

        S3File getChildFile(final String name);

        S3ObjectInputStream getValueAsStream();

        String getValueAsString();

        S3OutputStream setValueAsStream();

        void setValueAsString(final String value);

        String getETag();

        long getSize();

        Date getLastModified();
    }

    /**
     * S3 does not actually have the concept of directories.
     *
     * They can be implied, however, as you are allowed to
     * use slashes in the names of Objects as well as query
     * Objects that live only under a specific prefix (path).
     *
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
        public S3File getChildFile(final String name) {
            final Path child = path.getChild(name);

            if (child.isDirectory()) {

                return new S3File(bucket, child, new Directory());

            } else {

                return new S3File(bucket, child, new NewObject());
            }
        }

        @Override
        public S3ObjectInputStream getValueAsStream() {
            throw new UnsupportedOperationException("S3File refers to a directory");
        }

        @Override
        public String getValueAsString() {
            throw new UnsupportedOperationException("S3File refers to a directory");
        }

        @Override
        public S3OutputStream setValueAsStream() {
            throw new UnsupportedOperationException("S3File refers to a directory");
        }

        @Override
        public void setValueAsString(final String value) {
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
        public Date getLastModified() {
            throw new UnsupportedOperationException("S3File refers to a directory");
        }
    }

    /**
     * When looking up a single object, the Amazon S3 API
     * will return an S3Object instance.  This serves as
     * the Node adapter for that form of representing the
     * common metadata.
     */
    private class Object implements Node {
        private final S3Object object;

        public Object(final S3Object object) {
            this.object = object;
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
        public S3File getChildFile(final String name) {
            final String message = String.format("S3File '%s' is a not directory and cannot have child '%s'", path.getAbsoluteName(), name);
            throw new UnsupportedOperationException(message);
        }

        @Override
        public S3ObjectInputStream getValueAsStream() {
            return bucket.getObjectAsStream(object.getKey());
        }

        @Override
        public String getValueAsString() {
            return bucket.getObjectAsString(object.getKey());
        }

        @Override
        public S3OutputStream setValueAsStream() {
            return writeStreamAndReplace(this);
        }

        @Override
        public void setValueAsString(final String value) {
            writeStringAndReplace(this, value);
        }

        @Override
        public String getETag() {
            return object.getObjectMetadata().getETag();
        }

        @Override
        public long getSize() {
            return object.getObjectMetadata().getContentLength();
        }

        @Override
        public Date getLastModified() {
            return object.getObjectMetadata().getLastModified();
        }
    }

    /**
     * When listing Objects in S3 via the AmazonS3 API you will
     * get several S3ObjectSummary instances which have similar
     * data as S3Object but of course in different places.
     */
    private class ObjectSummary implements Node {
        private final S3ObjectSummary summary;

        public ObjectSummary(final S3ObjectSummary summary) {
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
        public S3File getChildFile(final String name) {
            final String message = String.format("S3File '%s' is a not directory and cannot have child '%s'", path.getAbsoluteName(), name);
            throw new UnsupportedOperationException(message);
        }

        @Override
        public S3ObjectInputStream getValueAsStream() {
            return bucket.getObjectAsStream(summary.getKey());
        }

        @Override
        public String getValueAsString() {
            return bucket.getObjectAsString(summary.getKey());
        }

        @Override
        public S3OutputStream setValueAsStream() {
            return writeStreamAndReplace(this);
        }

        @Override
        public void setValueAsString(final String value) {
            writeStringAndReplace(this, value);
        }

        @Override
        public String getETag() {
            return summary.getETag();
        }

        @Override
        public long getSize() {
            return summary.getSize();
        }

        @Override
        public Date getLastModified() {
            return summary.getLastModified();
        }
    }

    /**
     * Any time an Object in S3 is updated we will have new last modified times,
     * new file size and new metadata.  When this happens we replace the existing
     * Node with a new one that represents the updated data.
     */
    private class UpdatedObject implements Node {
        private final PutObjectResult result;

        public UpdatedObject(final PutObjectResult result) {
            this.result = result;
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
        public S3File getChildFile(final String name) {
            final String message = String.format("S3File '%s' is a not directory and cannot have child '%s'", path.getAbsoluteName(), name);
            throw new UnsupportedOperationException(message);
        }

        @Override
        public S3ObjectInputStream getValueAsStream() {
            return bucket.getObjectAsStream(path.getAbsoluteName());
        }

        @Override
        public String getValueAsString() {
            return bucket.getObjectAsString(path.getAbsoluteName());
        }

        @Override
        public S3OutputStream setValueAsStream() {
            return writeStreamAndReplace(this);
        }

        @Override
        public void setValueAsString(final String value) {
            writeStringAndReplace(this, value);
        }

        @Override
        public String getETag() {
            return result.getETag();
        }

        @Override
        public long getSize() {
            return result.getMetadata().getContentLength();
        }

        @Override
        public Date getLastModified() {
            return result.getMetadata().getLastModified();
        }
    }

    /**
     * Represents an object that may not yet have been created and
     * therefore has no metadata.  It can be written, but not read.
     *
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
            return true;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public S3File getChildFile(final String name) {
            final String message = String.format("S3File '%s' is a not defined as a directory and cannot have child '%s'", path.getAbsoluteName(), name);
            throw new UnsupportedOperationException(message);
        }

        @Override
        public S3ObjectInputStream getValueAsStream() {
            final String message = String.format("S3File '%s' has not yet been created", path.getAbsoluteName());
            throw new UnsupportedOperationException(message);
        }

        @Override
        public String getValueAsString() {
            final String message = String.format("S3File '%s' has not yet been created", path.getAbsoluteName());
            throw new UnsupportedOperationException(message);
        }

        @Override
        public S3OutputStream setValueAsStream() {
            return writeStreamAndReplace(this);
        }

        @Override
        public void setValueAsString(final String value) {
            writeStringAndReplace(this, value);
        }

        @Override
        public String getETag() {
            final String message = String.format("S3File '%s' has not yet been created", path.getAbsoluteName());
            throw new UnsupportedOperationException(message);
        }

        @Override
        public long getSize() {
            final String message = String.format("S3File '%s' has not yet been created", path.getAbsoluteName());
            throw new UnsupportedOperationException(message);
        }

        @Override
        public Date getLastModified() {
            final String message = String.format("S3File '%s' has not yet been created", path.getAbsoluteName());
            throw new UnsupportedOperationException(message);
        }
    }

    private void writeStringAndReplace(final Node current, final String value) {
        final PutObjectResult result = bucket.setObjectAsString(path.getAbsoluteName(), value);
        node.compareAndSet(current, new UpdatedObject(result));
    }

    private S3OutputStream writeStreamAndReplace(final Node current) {
        return new S3OutputStream(bucket.getClient().getS3(), bucket.getName(), path.getAbsoluteName(), () -> {
            final S3Object object = bucket.getObject(path.getAbsoluteName());
            node.compareAndSet(current, new Object(object));
        });
    }
}