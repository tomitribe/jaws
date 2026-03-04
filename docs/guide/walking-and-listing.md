# Listing & Recursion

JAWS supports two modes of traversing an S3 key hierarchy: **immediate listing** (the default)
and **recursive listing** (with `@Recursive`). Understanding the difference is key to writing
efficient S3 code.

## Immediate Listing (Default)

Without `@Recursive`, a stream-returning method performs a single-level listing. What
"single-level" means depends on the element type:

```java
public interface Repository extends S3.Dir {
    Stream<S3File> files();        // flat, recursive — all descendant objects (S3.Dir method)
    Stream<S3File> list();         // delimiter-based — immediate children only (S3.Dir method)
    Stream<GroupDir> groups();     // GroupDir extends S3.Dir → immediate dirs only
    Stream<Artifact> artifacts();  // Artifact extends S3.File → immediate files only
}
```

Under the hood, this issues a single `ListObjects` request (plus pagination if the
bucket has more than 1,000 matching keys).

## Recursive Listing

The `@Recursive` annotation switches to recursive traversal. The behavior depends on
the element type:

- **`Stream<S3File>`** — uses `files()`, a single flat request returning all descendant objects
- **`Stream<X extends S3.Dir>`** — uses `walk()`, a multi-request tree traversal returning
  only directories

If the element type extends `S3.File`, **omit `@Recursive`** for best performance.
A recursive listing already returns only files, so there's no benefit.

```java
public interface Repository extends S3.Dir {
    @Recursive
    Stream<S3File> everything();
}
```

### AWS Request Cost

For `@Recursive Stream<S3File>`, the listing uses a single flat `ListObjects` request
(no delimiter) plus pagination. This is the most efficient way to get all objects.

For `@Recursive Stream<X extends S3.Dir>`, each prefix visited in the walk generates
at least one `ListObjects` request. If a single response is truncated (more than
1,000 keys), additional paginated requests are made automatically.

Consider this bucket structure:

```text
repository/
  org/
    apache/
      maven/
        maven-core/
          3.9.6/
            maven-core-3.9.6.jar
            maven-core-3.9.6.pom
      tomcat/
        tomcat/
          10.1.18/
            tomcat-10.1.18.jar
  com/
    google/
      guava/
        guava/
          33.0/
            guava-33.0.jar
```

A `@Recursive Stream<S3.Dir>` from `repository/` visits every prefix, issuing
**11 requests** — one for each directory level.

## Combining @Recursive with @Prefix

`@Prefix` narrows which keys the recursive listing returns. The prefix is sent
server-side, so AWS never returns keys that don't match:

```java
public interface Repository extends S3.Dir {
    // Recursively list only keys under the org/ namespace
    // com/ and junit/ are never fetched
    @Recursive
    @Prefix("org/")
    Stream<S3File> orgArtifacts();
}
```

In a large repository with thousands of groupIds, `@Prefix("org/apache/")`
can significantly reduce the data transferred.

## Combining @Recursive with @Delimiter

By default, `@Recursive Stream<S3.Dir>` uses `"/"` as the delimiter. The
`@Delimiter` annotation lets you change how the hierarchy is split:

```java
public interface DateIndex extends S3.Dir {
    // Keys like "2025-01-15", "2025-02-20"
    // Delimiter "-" splits on hyphens
    @Recursive
    @Delimiter("-")
    Stream<S3.Dir> byDate();
}
```

## Choosing the Right Approach

| Goal | Approach | Requests |
|---|---|---|
| Immediate children only | `Stream<X>` (no `@Recursive`) | 1 + pagination |
| All descendant objects | `@Recursive Stream<S3File>` | 1 + pagination |
| All descendant directories | `@Recursive Stream<X extends S3.Dir>` | 1 per prefix |
| Subtree only | `@Recursive @Prefix("sub/") Stream<S3File>` | 1 + pagination |
| Single level, dirs only | `Stream<X>` where X extends `S3.Dir` | 1 + pagination |
| Single level, files only | `Stream<X>` where X extends `S3.File` | 1 + pagination |
