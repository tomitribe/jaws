# S3Bucket

Represents a single S3 bucket and provides operations for reading, writing,
listing, uploading, and downloading objects within it.

## Obtaining an S3Bucket

```java
S3Bucket bucket = s3Client.getBucket("my-bucket");
// or
S3Bucket bucket = s3Client.createBucket("new-bucket");
```

## Creating Proxies

### as

```java
<T> T as(Class<T> clazz)
```

Creates a strongly-typed proxy for the bucket root. The given class should be
an interface extending `S3`, `S3.Dir`, or `S3.File`.

```java
Catalog catalog = bucket.as(Catalog.class);
```

### root

```java
S3File root()
```

Returns an `S3File` representing the bucket root directory.

## Listing Objects

### objects

```java
Stream<S3File> objects()
```

Returns all objects in the bucket as a flat listing.

```java
Stream<S3File> objects(ListObjectsRequest request)
```

Returns objects matching the given listing request. The request's bucket is
set automatically.

## Reading Objects

### getObject

```java
ResponseInputStream<GetObjectResponse> getObject(String key)
```

Returns a blocking input stream for the object with the given key.

### getObjectMetadata

```java
HeadObjectResponse getObjectMetadata(String key)
```

Returns the metadata for the object via a HEAD request.

### getFile

```java
S3File getFile(String key)
```

Returns an `S3File` for the object, pre-populated with metadata from a HEAD request.

## Writing Objects

### putObject

```java
PutObjectResponse putObject(String key, File file)
PutObjectResponse putObject(String key, InputStream inputStream, long contentLength)
PutObjectResponse putObject(String key, String content)
```

Uploads content as an S3 object.

### setObjectAsString / setObjectAsFile / setObjectAsStream

```java
PutObjectResponse setObjectAsString(String key, String value)
PutObjectResponse setObjectAsFile(String key, File value)
PutObjectResponse setObjectAsStream(String key, InputStream value)
```

Replaces the content of an existing object.

## Transfers

### upload

```java
Upload upload(String key, File file)
Upload upload(String key, InputStream input, long size)
```

Uploads using the S3 Transfer Manager, which handles multipart uploads automatically.

### download

```java
FileDownload download(String key, File destination)
```

Downloads an S3 object to a local file using the S3 Transfer Manager.

## Other

### deleteObject

```java
void deleteObject(String key)
```

Deletes the object with the given key.

### getName / getCreationDate / getClient

```java
String getName()
Instant getCreationDate()
S3Client getClient()
```

Bucket metadata accessors.
