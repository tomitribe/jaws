# @Walk

Marks a proxy method as performing a recursive walk of the S3 key hierarchy.

## Declaration

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Walk {
    int maxDepth() default -1;
    int minDepth() default 0;
}
```

## Attributes

| Attribute | Type | Default | Description |
|---|---|---|---|
| `maxDepth` | `int` | `-1` (unlimited) | Maximum depth to traverse |
| `minDepth` | `int` | `0` | Minimum depth at which results are included |

## Description

Without `@Walk`, stream-returning methods use a single-level delimiter-based
listing. With `@Walk`, the listing descends recursively into child prefixes,
similar to `java.nio.file.Files.walk()`.

The annotated method must return `Stream<S3File>`, `List<S3File>`, `S3File[]`,
or a collection type whose element type is `S3File` or an interface extending `S3`.

## Examples

### Unlimited walk

```java
public interface Repository extends S3.Dir {
    @Walk
    Stream<S3File> everything();
}
```

### Controlled depth

```java
public interface Repository extends S3.Dir {
    // Walk at most 2 levels deep
    @Walk(maxDepth = 2)
    Stream<S3File> shallow();

    // Only return results at depth 2 and below
    @Walk(minDepth = 2)
    Stream<S3File> deepOnly();

    // Only results at exactly depth 2
    @Walk(minDepth = 2, maxDepth = 2)
    Stream<S3File> exactlyTwoLevelsDeep();
}
```

### Walk with prefix narrowing

```java
public interface Repository extends S3.Dir {
    // Walk only the org/ namespace — com/ and junit/
    // are never fetched from AWS
    @Walk
    @Prefix("org/")
    Stream<S3File> orgArtifacts();
}
```

### Walk with custom delimiter

```java
public interface DateIndex extends S3.Dir {
    // Keys like "2025-01-15", "2025-02-20"
    @Walk
    @Delimiter("-")
    Stream<S3File> byDate();
}
```

## AWS Request Cost

Each level of the walk issues one `ListObjects` request per prefix (directory)
visited. If a single response is truncated (more than 1,000 keys), additional
paginated requests are made automatically.

The total number of requests is:

```
requests = sum of ceil(entries / 1000) for each prefix visited
```

### Example

Consider a bucket with this structure:

```
photos/
  2024/
    jan/   (50 files)
    feb/   (50 files)
  2025/
    jan/   (50 files)
```

An unlimited `@Walk` from `photos/` issues **5 requests**: one for `photos/`,
one each for `2024/` and `2025/`, and one each for the month prefixes.

| Annotation | Prefixes visited | Requests |
|---|---|---|
| `@Walk` | all 5 | 5 |
| `@Walk(maxDepth = 1)` | `photos/`, `2024/`, `2025/` | 3 |
| `@Walk(maxDepth = 2)` | all 5 | 5 |
| `@Walk(minDepth = 2)` | all 5 (results filtered) | 5 |

!!! info
    `maxDepth` reduces requests by limiting which prefixes are descended into.
    `minDepth` does **not** reduce requests — all levels up to `maxDepth` are
    still traversed; results are simply filtered before being returned.

## See Also

- [Walking & Listing](../../guide/walking-and-listing.md) — detailed walkthrough
- [@Prefix](prefix.md) — narrow which subtrees are entered
- [@Delimiter](delimiter.md) — change how the hierarchy is split
