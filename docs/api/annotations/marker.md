# @Marker

Sets the starting position for a listing in the `ListObjects` request.

## Declaration

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Marker {
    String value();
}
```

## Attributes

| Attribute | Type | Description |
|---|---|---|
| `value` | `String` | The key to start listing from (exclusive) |

## Description

The marker indicates where in the bucket to begin listing. The list will only
include keys that occur lexicographically **after** the marker. This is the
low-level pagination mechanism used by S3.

In most cases, JAWS handles pagination automatically — truncated responses
are followed up with additional requests using the `nextMarker` from the
previous response. `@Marker` is useful when you need to start a listing
from a known position, such as resuming a previously interrupted scan.

## Examples

### Skip to a known position

```java
public interface Archive extends S3.Dir {
    @Marker("2024-01-01")
    Stream<S3File> since2024();
}
```

### Combine with @Prefix

```java
public interface Logs extends S3.Dir {
    // Only keys starting with "access-" and after "access-2024-06"
    @Prefix("access-")
    @Marker("access-2024-06")
    Stream<S3File> recentAccessLogs();
}
```

!!! note
    The marker value is compared lexicographically against the full S3 key.
    Results begin with the first key that is strictly greater than the marker.

## See Also

- [@Prefix](prefix.md) — narrow results by key prefix
- [@Delimiter](delimiter.md) — control directory grouping
