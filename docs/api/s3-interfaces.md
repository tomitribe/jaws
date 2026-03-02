# S3 Interfaces

The `S3` interface hierarchy provides the foundation for building strongly-typed
proxies. Your interfaces extend one of these base types to get the appropriate
behavior.

## S3

The base interface. Provides access to the backing `S3File` and parent navigation.

```java
public interface S3 {
    S3File file();     // the S3File backing this proxy
    S3File parent();   // the parent S3File
}
```

Use `S3` as the base when your interface doesn't need directory listing
or file content methods.

## S3.Dir

Extends `S3` with directory (prefix) operations. Use this when your interface
models a container of S3 objects.

```java
public interface Dir extends S3 {
    S3File file(String name);            // child lookup
    Stream<S3File> files();              // all descendants (flat)
    Stream<S3File> list();               // immediate children
    Upload upload(File file);            // upload by filename
    Upload upload(File file, TransferListener listener);
}
```

### Return-type dispatch

When a proxy method returns `Stream<X>` where `X extends S3.Dir`, the library
uses a delimiter-based listing and returns **only directories**.

```java
public interface Root extends S3.Dir {
    Stream<SubDir> subdirs();  // SubDir extends S3.Dir → dirs only
}
```

## S3.File

Extends `S3` with object content and metadata methods. Use this when your
interface models a single S3 object.

```java
public interface File extends S3 {
    InputStream getValueAsStream();
    String getValueAsString();
    void setValueAsStream(InputStream is);
    void setValueAsString(String value);
    void setValueAsFile(java.io.File file);
    String getETag();
    long getSize();
    Instant getLastModified();
    ObjectMetadata getObjectMetadata();
}
```

All methods are provided as defaults that delegate to the backing `S3File`.

### Return-type dispatch

When a proxy method returns `Stream<X>` where `X extends S3.File`, the library
uses a delimiter-based listing and returns **only files** (not directories).

```java
public interface Album extends S3.Dir {
    Stream<Photo> photos();  // Photo extends S3.File → files only
}
```

## Summary

| Base type | Use case | Listing dispatch |
|---|---|---|
| `S3` | Navigation only | Standard (flat) |
| `S3.Dir` | Directory / container | `Stream<X extends Dir>` → dirs only |
| `S3.File` | Object with content | `Stream<X extends File>` → files only |
