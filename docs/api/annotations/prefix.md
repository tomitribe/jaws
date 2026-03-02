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

`@Prefix` works on both flat listing methods and `@Walk` methods.

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

On a method without `@Walk`, the prefix narrows a flat listing:

```java
public interface Repository extends S3.Dir {
    @Prefix("org/apache")
    Stream<S3File> apacheArtifacts();
}
```

### Recursive walk

Combined with `@Walk`, the prefix limits which subtrees are entered,
reducing both the number of AWS requests and the volume of results:

```java
public interface Repository extends S3.Dir {
    // Walk only the org/ namespace — com/ and junit/
    // are never fetched from AWS
    @Walk
    @Prefix("org/")
    Stream<S3File> orgArtifacts();

    // Walk only org/apache/ artifacts, two levels deep
    @Walk(maxDepth = 2)
    @Prefix("org/apache/")
    Stream<S3File> apacheProjects();
}
```

Without `@Prefix`, the walk descends into every namespace in the repository,
issuing a `ListObjects` request for each prefix visited. With
`@Prefix("org/apache/")`, only subtrees under `org/apache/` are returned
by AWS. In a large repository with thousands of groupIds, this can reduce
requests from thousands to a handful.

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

## See Also

- [@Walk](walk.md) — recursive traversal
- [@Suffix](suffix.md) — client-side suffix filtering
- [@Matches](matches.md) — client-side regex filtering
- [@Filter](filter.md) — client-side filtering with arbitrary predicates
- [Filtering](../../guide/filtering.md) — server-side vs client-side filtering
