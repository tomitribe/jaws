# JAWS — Java AWS S3

**Strongly-typed, proxy-based Java abstraction for Amazon S3.**

JAWS lets you define plain Java interfaces that map directly to your S3 bucket structure.
The library creates dynamic proxies that translate method calls into efficient S3 API
operations — no boilerplate, no manual key construction, no pagination code.

## Why JAWS?

Working with the AWS S3 SDK directly means writing repetitive code to build requests,
handle pagination, and assemble key paths. JAWS replaces all of that with a clean
interface-driven model:

```java
// Define your bucket structure as interfaces
public interface Catalog extends S3.Dir {
    Stream<Category> categories();
    Category category(String name);
}

public interface Category extends S3.Dir {
    Stream<Product> products();
}

public interface Product extends S3.File {
    // inherits getValueAsString(), getSize(), getLastModified(), etc.
}

// Use it
S3Client s3 = new S3Client(S3AsyncClient.builder().build());
S3Bucket bucket = s3.getBucket("my-catalog");
Catalog catalog = bucket.as(Catalog.class);

catalog.categories().forEach(category -> {
    System.out.println(category.file().getName());
    category.products().forEach(product -> {
        System.out.println("  " + product.file().getName() + " (" + product.getSize() + " bytes)");
    });
});
```

## Key Features

- **Typed proxies** — Define interfaces, get proxies. Method names map to S3 keys.
- **Directory & File types** — `S3.Dir` for containers, `S3.File` for objects with content.
  Return types drive listing behavior automatically.
- **Annotations** — `@Name`, `@Parent`, `@Walk`, `@Prefix`, `@Filter`, `@Delimiter`
  give fine-grained control over key resolution, traversal, and filtering.
- **Efficient S3 usage** — Delimiter-based listings, server-side prefix filtering, and
  controlled walk depth minimize AWS API requests.
- **Upload & Download** — Built-in support via the S3 Transfer Manager with progress tracking.

## Quick Start

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>org.tomitribe</groupId>
    <artifactId>jaws-s3</artifactId>
    <version>2.0-SNAPSHOT</version>
</dependency>
```

Then head to [Getting Started](getting-started.md) for a complete walkthrough.
