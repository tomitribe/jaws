# Filtering

JAWS provides two filtering mechanisms: **server-side** filtering with `@Prefix`
and **client-side** filtering with `@Filter`. Use server-side filtering whenever
possible to reduce the data transferred from AWS.

## Server-Side Filtering with @Prefix

The `@Prefix` annotation sends a prefix filter as part of the `ListObjects` request.
AWS returns only keys that begin with the specified prefix:

```java
public interface Repository extends S3.Dir {
    @Prefix("org/apache")
    Stream<S3File> apacheArtifacts();
}
```

This is the most efficient approach — non-matching keys never leave AWS and
never consume bandwidth.

### Partial prefixes

The prefix can be any string, including a partial key segment:

```java
public interface Versions extends S3.Dir {
    @Prefix("10.")
    Stream<S3File> version10();  // 10.1.18, 10.1.19, ...

    @Prefix("9.0")
    Stream<S3File> version9();   // 9.0.85, 9.0.86, ...
}
```

## Client-Side Filtering with @Filter

The `@Filter` annotation applies a `Predicate<S3File>` after results are returned
from AWS. The predicate class must have a no-arg constructor:

```java
public class IsJar implements Predicate<S3File> {
    @Override
    public boolean test(S3File file) {
        return file.getName().endsWith(".jar");
    }
}

public class IsSnapshot implements Predicate<S3File> {
    @Override
    public boolean test(S3File file) {
        return file.getName().contains("SNAPSHOT");
    }
}

public interface VersionDir extends S3.Dir {
    @Filter(IsJar.class)
    Stream<S3File> jars();

    @Filter(IsSnapshot.class)
    Stream<S3File> snapshots();

    @Filter(IsJar.class)
    @Filter(IsSnapshot.class)
    Stream<S3File> snapshotJars();  // both filters applied (AND)
}
```

## Type-Level Filters

`@Filter` can also be placed on the element type interface itself. The filter
then applies automatically to every listing method that returns that type:

```java
@Filter(IsJar.class)
public interface JarFile extends S3.File {
    // Any Stream<JarFile> listing will only include .jar files
}

public interface VersionDir extends S3.Dir {
    Stream<JarFile> artifacts();  // automatically filtered to .jar files
    Stream<S3File> everything();  // no filter applied
}
```

### Filter ordering

When both interface-level and method-level filters are present, they are
combined with AND logic. Interface-level filters run first:

```java
@Filter(IsJar.class)              // 1st: must be .jar
public interface JarFile extends S3.File {}

public interface VersionDir extends S3.Dir {
    @Filter(IsSnapshot.class)     // 2nd: must also be SNAPSHOT
    Stream<JarFile> snapshotJars();
}
```

## Combining @Prefix and @Filter

For maximum efficiency, use `@Prefix` to reduce the result set server-side,
then `@Filter` for criteria that can't be expressed as a key prefix:

```java
public interface Repository extends S3.Dir {
    // Server-side: only keys starting with "org/apache"
    // Client-side: only .jar files
    @Prefix("org/apache")
    @Filter(IsJar.class)
    Stream<S3File> apacheJars();
}
```
