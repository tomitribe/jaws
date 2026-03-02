# @Delimiter

Sets the delimiter for the `ListObjects` request used to query S3.

## Declaration

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Delimiter {
    String value();
}
```

## Attributes

| Attribute | Type | Description |
|---|---|---|
| `value` | `String` | The delimiter character or string |

## Description

S3 uses delimiters to simulate a directory hierarchy within a flat key space.
The default delimiter is `"/"`, which treats slashes in keys as directory
separators. `@Delimiter` allows changing this for keys that use a different
separator.

`@Delimiter` works on both flat listing methods and `@Walk` methods. When
used with `@Walk`, the delimiter controls how the walk discovers child
prefixes at each level.

## Examples

### Flat listing with a custom delimiter

```java
public interface Logs extends S3.Dir {
    // Keys like "2025-01-15-access.log", "2025-01-15-error.log"
    @Delimiter("-")
    Stream<S3File> bySegment();
}
```

### Walk with a custom delimiter

```java
public interface DateIndex extends S3.Dir {
    // Keys like "2025-01-15", "2025-02-20"
    // Delimiter "-" splits on hyphens, creating a year/month/day hierarchy
    @Walk
    @Delimiter("-")
    Stream<S3File> byDate();
}
```

### No delimiter

An empty string disables delimiter-based grouping entirely:

```java
public interface Bucket extends S3.Dir {
    // Returns all keys as a flat list, no directory grouping
    @Delimiter("")
    Stream<S3File> allKeys();
}
```

## See Also

- [@Walk](walk.md) — recursive traversal (defaults to `"/"` delimiter)
- [Walking & Listing](../../guide/walking-and-listing.md) — how delimiters affect listings
