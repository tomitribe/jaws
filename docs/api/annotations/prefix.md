# @Prefix

Narrows a listing or walk to only S3 keys that begin with the given prefix.

## Declaration

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Prefix {
    String value();
}
```

## Attributes

| Attribute | Type | Description |
|---|---|---|
| `value` | `String` | The prefix to filter by, relative to the current proxy's position |

## Description

The prefix value is relative — it is appended to the current proxy's position
in the key hierarchy. The prefix is sent server-side in the `ListObjects`
request, so keys that do not match are never returned by AWS and never consume
bandwidth or processing.

`@Prefix` works on both immediate listing methods and `@Recursive` methods.

## Examples

Consider a Maven repository stored in S3:

```text
repository/
  org/apache/maven/maven-core/3.9.6/maven-core-3.9.6.jar
  org/apache/maven/maven-core/3.9.6/maven-core-3.9.6.pom
  org/apache/tomcat/tomcat/10.1.18/tomcat-10.1.18.jar
  com/google/guava/guava/33.0/guava-33.0.jar
  junit/junit/4.13.2/junit-4.13.2.jar
```

### Flat listing

On a method without `@Recursive`, the prefix narrows an immediate listing:

```java
public interface Repository extends S3.Dir {
    @Prefix("org/apache")
    Stream<S3File> apacheArtifacts();
}
```

### Recursive listing

Combined with `@Recursive`, the prefix limits which keys are returned,
reducing both the bandwidth and the volume of results:

```java
public interface Repository extends S3.Dir {
    // Recursively list only keys under the org/ namespace
    // — com/ and junit/ are never fetched from AWS
    @Recursive
    @Prefix("org/")
    Stream<S3File> orgArtifacts();
}
```

Without `@Prefix`, the recursive listing returns every key in the
directory. With `@Prefix("org/")`, only keys under `org/` are returned
by AWS. In a large repository with thousands of groupIds, this can
significantly reduce the data transferred.

### Partial prefixes

The prefix does not need to end with a delimiter — it can be any key prefix,
including a partial name. This is useful for matching a subset of entries
within a single directory:

```java
public interface Tomcat extends S3.Dir {
    // Only version directories starting with "10."
    // Matches 10.1.18/, 10.1.19/, etc.
    // Skips 9.0.85/, 11.0.0/, etc.
    @Prefix("10.")
    Stream<S3File> version10();
}

S3File tomcatDir = bucket.getFile("org/apache/tomcat/tomcat");
Tomcat tomcat = tomcatDir.as(Tomcat.class);
tomcat.version10().forEach(v -> System.out.println(v.getName()));
```

Because the prefix is applied server-side, AWS returns only the matching
keys. A directory with hundreds of version entries can be efficiently
narrowed to just the relevant subset.

## Input Validation on Single-Arg Methods

When `@Prefix` is placed on a single-arg proxy method, it validates that
the input name starts with the prefix. If not, an `IllegalArgumentException`
is thrown:

```java
public interface Logs extends S3.Dir {
    @Prefix("app-")
    Stream<S3File> appLogs();            // server-side prefix filter

    @Prefix("app-")
    S3File appLog(String name);          // validates name starts with "app-"
}

logs.appLog("app-2025-03-01.log");       // OK
logs.appLog("system-2025-03-01.log");    // throws IllegalArgumentException
```

Note that `@Prefix` is method-only (`@Target(ElementType.METHOD)`), so it
cannot be placed on a type. For listing methods, the prefix is applied
server-side in the `ListObjects` request. For single-arg methods, it is
checked client-side against the input name.

## See Also

- [@Recursive](recursive.md) — recursive listing
- [@Suffix](suffix.md) — client-side suffix filtering
- [@Match](match.md) — client-side regex filtering
- [@Filter](filter.md) — client-side filtering with arbitrary predicates
- [Filtering](../../guide/filtering.md) — server-side vs client-side filtering
