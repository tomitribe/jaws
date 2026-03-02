# Filtering

JAWS provides several filtering mechanisms. **Server-side** filtering with
`@Prefix` reduces the data transferred from AWS. **Client-side** filtering
with `@Suffix`, `@Match`, and `@Filter` refines results once they arrive.

Use server-side filtering whenever possible, then layer on client-side
filters from simplest to most complex.

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

## Client-Side Filtering with @Suffix

The `@Suffix` annotation filters by file name ending. It is the simplest
client-side filter — a shorthand for the very common case of filtering by
file extension:

```java
public interface Assets extends S3.Dir {
    @Suffix(".css")
    Stream<S3File> stylesheets();

    @Suffix(".js")
    Stream<S3File> scripts();
}
```

Multiple suffixes are OR'd — a file matches if its name ends with any of them:

```java
public interface Assets extends S3.Dir {
    @Suffix({".jpg", ".png", ".gif"})
    Stream<S3File> images();
}
```

### Excluding by suffix

Use `exclude = true` to remove entries matching a suffix. `@Suffix` is
repeatable, so you can combine include and exclude annotations:

```java
public interface Assets extends S3.Dir {
    // Everything except JavaScript files
    @Suffix(value = ".js", exclude = true)
    Stream<S3File> noJavaScript();

    // All .jar files except sources and javadoc jars
    @Suffix(".jar")
    @Suffix(value = {"-sources.jar", "-javadoc.jar"}, exclude = true)
    Stream<S3File> binaryJars();
}
```

Include annotations are applied first, then exclude annotations carve out
exceptions from the included set.

## Client-Side Filtering with @Match

The `@Match` annotation filters by a regular expression on the file name.
The entire name must match (implied `^` and `$`):

```java
public interface Reports extends S3.Dir {
    // Match daily-2025-01-15.csv, daily-2025-02-01.csv, etc.
    @Match("daily-\\d{4}-\\d{2}-\\d{2}\\.csv")
    Stream<S3File> dailyReports();

    // Match .jpg or .png files
    @Match(".*\\.(jpg|png)")
    Stream<S3File> images();
}
```

!!! warning
    `@Match` uses `Pattern.asMatchPredicate()`, so the pattern must match
    the **entire** file name. `@Match("css")` matches nothing — no file is
    named exactly "css". Use `@Match(".*\\.css")` instead.

### Excluding by regex

Use `exclude = true` to remove entries matching a pattern. `@Match` is
repeatable, so you can combine include and exclude annotations:

```java
public interface Assets extends S3.Dir {
    // All CSS files except reset.css
    @Match(".*\\.css")
    @Match(value = "reset\\.css", exclude = true)
    Stream<S3File> cssExceptReset();
}
```

## Client-Side Filtering with @Filter

The `@Filter` annotation applies an arbitrary `Predicate<S3File>` after results
are returned from AWS. The predicate class must have a no-arg constructor:

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

!!! tip
    If your filter is just checking a suffix, use `@Suffix` instead. If it's
    a name pattern, use `@Match`. Reserve `@Filter` for cases that need
    access to the full `S3File` (e.g., size, metadata, path components).

## Type-Level Filters

`@Suffix`, `@Match`, and `@Filter` can all be placed on the element type
interface itself. The filter then applies automatically to every listing
method that returns that type:

```java
@Suffix(".parquet")
public interface ParquetFile extends S3.File {
    // Any Stream<ParquetFile> listing will only include .parquet files
}

@Match("\\d{4}-\\d{2}-\\d{2}\\.csv")
public interface DailyReport extends S3.File {
    // Any Stream<DailyReport> listing will only include date-named CSVs
}

@Filter(IsJar.class)
public interface JarFile extends S3.File {
    // Any Stream<JarFile> listing will only include .jar files
}

public interface DataDir extends S3.Dir {
    Stream<ParquetFile> data();      // automatically filtered to .parquet
    Stream<DailyReport> reports();   // automatically filtered to date CSVs
    Stream<JarFile> artifacts();     // automatically filtered to .jar
    Stream<S3File> everything();     // no filter applied
}
```

## Evaluation Order

When multiple filter annotations are present, they are applied in a
defined order — simplest and cheapest first, most complex last:

1. **@Prefix** — server-side, in the `ListObjects` request
2. **@Suffix** includes — client-side, `String.endsWith()`
3. **@Suffix** excludes
4. **@Match** includes — client-side, compiled regex
5. **@Match** excludes
6. **@Filter** — client-side, arbitrary `Predicate<S3File>`

All client-side filters are AND'd together. Each filter only sees entries
that already passed the previous ones. This means a `@Filter` predicate
can safely assume that `@Suffix` and `@Match` have already passed.

When both interface-level and method-level annotations are present,
interface-level filters run first within each category.

```java
@Suffix(".jar")                   // 1st: interface-level suffix
public interface JarFile extends S3.File {}

public interface VersionDir extends S3.Dir {
    @Filter(IsSnapshot.class)     // 2nd: method-level filter
    Stream<JarFile> snapshotJars();
}
```

## Combining Annotations

For maximum efficiency, use `@Prefix` to reduce the result set server-side,
then layer on client-side filters as needed:

```java
public interface Repository extends S3.Dir {
    // Server-side: only keys starting with "org/apache"
    // Client-side: only .jar files
    @Prefix("org/apache")
    @Suffix(".jar")
    Stream<S3File> apacheJars();

    // Server-side: only keys starting with "export-"
    // Client-side: only .parquet files matching a date pattern
    @Prefix("export-")
    @Suffix(".parquet")
    @Match("export-2025-.*\\.parquet")
    Stream<S3File> exports2025();

    // All three client-side filters together
    @Suffix(".jar")
    @Match(".*-SNAPSHOT\\.jar")
    @Filter(IsInRange.class)
    Stream<S3File> recentSnapshots();
}
```
