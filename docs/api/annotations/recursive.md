# @Recursive

Marks a listing method as recursive — all descendants are returned, not just
immediate children.

## Declaration

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Recursive {
}
```

## Description

Without `@Recursive`, all listing methods use a delimiter-based listing that
returns only immediate children at one level. With `@Recursive`, the listing
returns all descendants.

The behavior depends on the element type:

- **`Stream<S3File>`** — uses `files()`, a single flat request returning all
  descendant objects
- **`Stream<X extends S3.Dir>`** — uses `walk()`, a multi-request tree
  traversal returning directories only

If the element type extends `S3.File`, **omit `@Recursive`** for best
performance. A recursive listing already returns only files.

There is no depth control. For depth-limited traversal, use the
`S3File.walk()` methods directly.

## Examples

### Recursive listing of all objects

```java
public interface Repository extends S3.Dir {
    @Recursive
    Stream<S3File> everything();
}
```

### Recursive listing of directories

```java
public interface Deployment extends S3.Dir {
    @Recursive
    Stream<S3.Dir> layout();
}
```

### Recursive listing with prefix narrowing

```java
public interface Repository extends S3.Dir {
    // Recursively list only keys under the org/ namespace
    // — com/ and junit/ are never fetched from AWS
    @Recursive
    @Prefix("org/")
    Stream<S3File> orgArtifacts();
}
```

### Recursive listing with filter

```java
public interface Processed extends S3.Dir {
    @Recursive
    @Filter(IsParquet.class)
    Stream<S3File> allParquetFiles();
}
```

### Recursive listing with custom delimiter

```java
public interface DateIndex extends S3.Dir {
    // Keys like "2025-01-15", "2025-02-20"
    @Recursive
    @Delimiter("-")
    Stream<S3.Dir> byDate();
}
```

## AWS Request Cost

For `@Recursive Stream<S3File>` (and other non-`S3.Dir` types), the listing
uses a single flat `ListObjects` request (no delimiter) plus pagination if
needed. This is the most efficient way to get all objects.

For `@Recursive Stream<X extends S3.Dir>`, each prefix visited generates at
least one `ListObjects` request. If a single response is truncated (more
than 1,000 keys), additional paginated requests are made automatically.

## See Also

- [Listing & Recursion](../../guide/walking-and-listing.md) — detailed walkthrough
- [@Prefix](prefix.md) — narrow which keys are returned
- [@Delimiter](delimiter.md) — change how the hierarchy is split
