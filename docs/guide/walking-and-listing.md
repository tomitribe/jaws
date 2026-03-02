# Walking & Listing

JAWS supports two modes of traversing an S3 key hierarchy: **flat listing** (the default)
and **recursive walking** (with `@Walk`). Understanding the difference is key to writing
efficient S3 code.

## Flat Listing (Default)

Without `@Walk`, a stream-returning method performs a single-level listing. What
"single-level" means depends on the element type:

```java
public interface Repository extends S3.Dir {
    Stream<S3File> files();        // flat, recursive — all descendant objects
    Stream<S3File> list();         // delimiter-based — immediate children only
    Stream<GroupDir> groups();     // GroupDir extends S3.Dir → dirs only
    Stream<Artifact> artifacts();  // Artifact extends S3.File → files only
}
```

Under the hood, this issues a single `ListObjects` request (plus pagination if the
bucket has more than 1,000 matching keys).

## Recursive Walk

The `@Walk` annotation switches to recursive traversal. JAWS descends into each
child prefix, issuing a separate `ListObjects` request per prefix visited:

```java
public interface Repository extends S3.Dir {
    @Walk
    Stream<S3File> everything();
}
```

### Controlling Depth

Use `maxDepth` and `minDepth` to control the traversal:

```java
public interface Repository extends S3.Dir {
    // Walk at most 2 levels deep
    @Walk(maxDepth = 2)
    Stream<S3File> shallow();

    // Only results at depth 2 and below
    @Walk(minDepth = 2)
    Stream<S3File> deepOnly();

    // Only results at exactly depth 2
    @Walk(minDepth = 2, maxDepth = 2)
    Stream<S3File> exactlyTwo();
}
```

### AWS Request Cost

Each prefix visited in the walk generates at least one `ListObjects` request.
If a single response is truncated (more than 1,000 keys), additional paginated
requests are made automatically.

Consider this bucket structure:

```
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

An unlimited `@Walk` from `repository/` visits every prefix, issuing
**11 requests** — one for each directory level.

With `@Walk(maxDepth = 2)`, only `repository/`, `org/`, `apache/`, `com/`,
and `google/` are visited — **5 requests**.

!!! info
    `minDepth` does **not** reduce the number of requests. All levels up to
    `maxDepth` are still traversed; `minDepth` simply filters out results
    shallower than the specified depth before returning them.

## Combining @Walk with @Prefix

`@Prefix` narrows which subtrees the walk enters. The prefix is sent server-side,
so AWS never returns keys that don't match:

```java
public interface Repository extends S3.Dir {
    // Walk only the org/ namespace
    // com/ and junit/ are never fetched
    @Walk
    @Prefix("org/")
    Stream<S3File> orgArtifacts();

    // Walk only org/apache/ artifacts
    @Walk(maxDepth = 2)
    @Prefix("org/apache/")
    Stream<S3File> apacheProjects();
}
```

In a large repository with thousands of groupIds, `@Prefix("org/apache/")`
can reduce requests from thousands to a handful.

## Combining @Walk with @Delimiter

By default, `@Walk` uses `"/"` as the delimiter. The `@Delimiter` annotation
lets you change how the hierarchy is split:

```java
public interface DateIndex extends S3.Dir {
    // Keys like "2025-01-15", "2025-02-20"
    // Delimiter "-" splits on hyphens
    @Walk
    @Delimiter("-")
    Stream<S3File> byDate();
}
```

## Choosing the Right Approach

| Goal | Approach | Requests |
|---|---|---|
| Immediate children only | `Stream<X>` (no `@Walk`) | 1 + pagination |
| All descendants, every level | `@Walk` | 1 per prefix |
| Descendants to depth N | `@Walk(maxDepth = N)` | 1 per prefix up to N |
| Subtree only | `@Walk @Prefix("sub/")` | 1 per prefix in subtree |
| Single level, dirs only | `Stream<X>` where X extends `S3.Dir` | 1 + pagination |
| Single level, files only | `Stream<X>` where X extends `S3.File` | 1 + pagination |
