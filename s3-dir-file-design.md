# S3.Dir and S3.File — Type-Safe S3 Directory Modeling

## Overview

JAWS provides a proxy-based interface system for modeling S3 bucket structures as
type-safe Java interfaces. With `S3.Dir` and `S3.File`, the type hierarchy tells the
library whether something is a directory or a file — eliminating the need for
`@Walk(maxDepth=1)` annotations and type-discriminating `@Filter` predicates.

## Quick Example

```java
interface DownloadsBucket extends S3.Dir {

    Stream<Customer> customers();

    @Filter(CustomerId.class)
    Stream<Customer> premiumCustomers();
}

interface Customer extends S3.Dir {

    @Filter(ProductName.class)
    Stream<Product> products();

    @Name("name.txt")
    S3.File nameTxt();

    @Name("subscription.bin")
    S3.File subscription();
}

interface Product extends S3.Dir {

    @Filter(VersionName.class)
    Stream<Version> versions();
}

interface Version extends S3.Dir {

    @Name("changelog.txt")
    S3.File changelog();

    @Name("upload.meta")
    S3.File uploadMeta();
}
```

Usage:

```java
DownloadsBucket bucket = S3.of(DownloadsBucket.class, s3Client.getBucket("downloads"));

// List all customer directories — only commonPrefixes, no files
bucket.customers().forEach(customer -> {

    // Read file content through S3.File
    String name = customer.nameTxt().getValueAsString();

    // List product directories under this customer
    customer.products().forEach(product -> {
        product.versions().forEach(version -> {
            if (version.changelog().exists()) {
                String log = version.changelog().getValueAsString();
            }
        });
    });
});
```

## The Three Interfaces

### S3 — base type, shared by all

```java
interface S3 {
    S3File get();        // underlying S3File
    S3File parent();     // parent directory
}
```

Every user interface ultimately extends `S3`. The `get()` method gives access to the
raw `S3File` if needed for low-level operations.

### S3.Dir — directories

```java
interface S3.Dir extends S3 {
    S3File file(String name);       // get a child by name
    Stream<S3File> files();         // list files at this level (with delimiter)
    Stream<S3File> dirs();          // list subdirectories at this level
    Stream<S3File> list();          // list both files and directories
}
```

Extend `S3.Dir` when the interface represents a directory in S3:

```java
interface MyBucket extends S3.Dir { ... }
interface ProjectDir extends S3.Dir { ... }
```

### S3.File — files

```java
interface S3.File extends S3 {
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

Use `S3.File` as a return type for methods that reference files:

```java
@Name("config.json")
S3.File config();
```

## How Return Types Drive Behavior

The library inspects the generic type parameter of `Stream<X>` to determine how to
list S3 objects:

| Return type | What the library does |
|---|---|
| `Stream<X>` where X extends `S3.Dir` | `listObjects` with delimiter, processes only `commonPrefixes` |
| `Stream<X>` where X extends `S3.File` | `listObjects` with delimiter, processes only `contents` |
| `Stream<S3File>` | Flat listing, no delimiter (all objects recursively) |

This means you never need `@Walk(maxDepth=1)` just to list immediate children.
The return type says it all:

```java
// Before: needed @Walk to get one-level listing
@Walk(maxDepth = 1)
@Filter(ProductName.class)
Stream<Product> products();

// After: return type tells the library Product is a directory
@Filter(ProductName.class)
Stream<Product> products();
```

## Convenience Methods on S3.Dir

When you have a reference to an `S3.Dir`, you can list its contents directly:

```java
DownloadsBucket bucket = S3.of(DownloadsBucket.class, ...);

// List subdirectories only
bucket.dirs().forEach(dir -> System.out.println(dir.getName()));

// List files only
bucket.files().forEach(file -> System.out.println(file.getName()));

// List everything (like 'ls')
bucket.list().forEach(entry -> {
    if (entry.isDirectory()) {
        System.out.println(entry.getName() + "/");
    } else {
        System.out.println(entry.getName());
    }
});

// Get a specific child
S3File readme = bucket.file("README.md");
```

## Reading and Writing Files

`S3.File` provides direct content access:

```java
interface Config extends S3.Dir {
    @Name("settings.json")
    S3.File settings();
}

Config config = S3.of(Config.class, ...);

// Read
String json = config.settings().getValueAsString();
InputStream stream = config.settings().getValueAsStream();

// Write
config.settings().setValueAsString("{\"key\": \"value\"}");
config.settings().setValueAsFile(new File("/tmp/settings.json"));

// Metadata
long size = config.settings().getSize();
Instant modified = config.settings().getLastModified();
String etag = config.settings().getETag();
```

## @Walk Still Works for Recursive Traversal

`@Walk` is still available when you need recursive directory traversal:

```java
interface Repository extends S3.Dir {

    // Recursively walk all files
    @Walk
    @Filter(IsJar.class)
    Stream<S3File> allJars();

    // Walk with depth limits
    @Walk(maxDepth = 2, minDepth = 1)
    Stream<S3File> shallowEntries();
}
```

## @Filter Still Works

`@Filter` applies client-side predicate filtering to any stream:

```java
interface Customer extends S3.Dir {

    // All product directories
    Stream<Product> allProducts();

    // Only products matching the naming convention
    @Filter(ProductName.class)
    Stream<Product> products();
}

class ProductName implements Predicate<S3File> {
    public boolean test(S3File file) {
        String name = file.getName();
        return !name.contains(".") && name.equals(name.toLowerCase());
    }
}
```

## Navigation

Parent navigation works from any interface:

```java
interface Version extends S3.Dir {
    @Parent
    Product product();          // go up one level

    @Parent(2)
    Customer customer();        // go up two levels
}
```

Child navigation by name:

```java
interface Customer extends S3.Dir {
    Product product(String name);   // navigate to child directory by name
}

// Usage
Product p = customer.product("tribestream");
```
