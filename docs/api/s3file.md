# S3File

Represents a single S3 object or prefix (directory). `S3File` is the core
abstraction in JAWS — it tracks an object's key, metadata, and state, and
provides methods for reading, writing, listing, and navigating the key hierarchy.

## Obtaining an S3File

```java
// From a bucket
S3File file = bucket.getFile("path/to/object.txt");
S3File root = bucket.root();

// From a proxy
S3File backing = myProxy.file();
S3File parent = myProxy.parent();

// From another S3File
S3File child = parentFile.getFile("child-name");
S3File parent = file.getParentFile();
```

## Creating Proxies

### as

```java
<T> T as(Class<T> clazz)
```

Creates a strongly-typed proxy backed by this `S3File`. The given class should
be an interface extending `S3`, `S3.Dir`, or `S3.File`.

```java
S3File dir = bucket.root().getFile("catalog");
Catalog catalog = dir.as(Catalog.class);
```

## Content Access

### Reading

```java
String getValueAsString()
InputStream getValueAsStream()
```

Read the object's content. The first call fetches the content from S3; subsequent
calls may return cached content depending on the object's state.

### Writing

```java
void setValueAsString(String value)
void setValueAsStream(InputStream is)
void setValueAsFile(File file)
```

Replace the object's content. After writing, the `S3File` transitions to a
state that reflects the new content.

## Metadata

```java
String getETag()
long getSize()
Instant getLastModified()
ObjectMetadata getObjectMetadata()
```

Returns metadata about the object. Available metadata depends on how the
`S3File` was obtained (listing vs HEAD request vs content fetch).

## Navigation

```java
String getName()           // the last segment of the key
String getAbsoluteName()   // the full key
S3File getParentFile()     // parent directory
S3File getFile(String name) // child file
```

## Listing

```java
Stream<S3File> files()     // all descendants (flat, no delimiter)
Stream<S3File> list()      // immediate children (delimiter-based)
```

## State

```java
boolean exists()           // HEAD request to check existence
boolean isFile()           // true if this is an object (not a prefix)
boolean isDirectory()      // true if this is a prefix (directory)
```

## Uploads

```java
Upload upload(File file)
Upload upload(File file, TransferListener listener)
```

Upload a local file to this S3 location using the Transfer Manager.

## Deletion

```java
void delete()
```

Deletes the object from S3.
