# S3Client

Entry point for interacting with Amazon S3. Wraps an `S3AsyncClient` and an
`S3TransferManager`, providing bucket-level operations and a shared thread pool
for asynchronous transfers.

## Constructor

```java
S3Client(S3AsyncClient s3)
```

Creates a new `S3Client` backed by the given async client. A default thread pool
and `S3TransferManager` are created automatically.

**Example:**

```java
S3AsyncClient asyncClient = S3AsyncClient.builder().build();
S3Client s3 = new S3Client(asyncClient);

S3Bucket bucket = s3.getBucket("my-bucket");
Catalog catalog = bucket.as(Catalog.class);
```

## Methods

### createBucket

```java
S3Bucket createBucket(String name)
```

Creates a new S3 bucket with the given name and returns it.

### getBucket

```java
S3Bucket getBucket(String name)
```

Returns the bucket with the given name.

**Throws:** `NoSuchBucketException` if no bucket with that name exists.

### buckets

```java
Stream<S3Bucket> buckets()
```

Returns all buckets visible to the configured AWS credentials.

### getS3

```java
S3AsyncClient getS3()
```

Returns the underlying AWS async S3 client.

### getTransferManager

```java
S3TransferManager getTransferManager()
```

Returns the `S3TransferManager` used for upload and download operations.
