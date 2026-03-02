# Annotations

JAWS provides annotations to control how proxy methods map to S3 operations.
They can customize key resolution, traversal behavior, and result filtering.

## @Name

Overrides the S3 key segment used when resolving a method to a child object.
By default the method name is used; `@Name` allows mapping to keys that
can't be expressed as Java identifiers.

```java
public interface Module extends S3.Dir {
    @Name("pom.xml")
    PomFile pomXml();

    @Name("src/main/java")
    Sources sources();
}
```

Without `@Name`, `pomXml()` would resolve to the key segment `pomXml`.
With `@Name("pom.xml")`, it resolves to `pom.xml`.

## @Parent

Navigates upward in the S3 key hierarchy. The annotated method must return
an interface extending `S3`. The value specifies how many levels to go up
(default is 1).

```java
public interface Version extends S3.Dir {
    // Go up one level: version → artifact
    @Parent
    ArtifactDir artifact();

    // Go up two levels: version → artifact → group
    @Parent(2)
    GroupDir group();

    // Go up three levels: version → artifact → group → repository root
    @Parent(3)
    Repository repository();
}
```

This is useful for navigating from a deeply nested proxy back to an ancestor
without manually tracking the path.

!!! warning
    A `NoParentException` is thrown at runtime if the proxy is already at the
    bucket root and no further parent exists.

## @Walk

Marks a method as performing a recursive walk of the S3 key hierarchy, similar
to `java.nio.file.Files.walk()`. Without `@Walk`, stream-returning methods use
a single-level delimiter-based listing.

```java
public interface Repository extends S3.Dir {
    // Walk everything under this directory
    @Walk
    Stream<S3File> everything();

    // Walk at most 2 levels deep
    @Walk(maxDepth = 2)
    Stream<S3File> shallow();

    // Only return results at exactly depth 2
    @Walk(minDepth = 2, maxDepth = 2)
    Stream<S3File> exactlyTwoLevelsDeep();
}
```

### Parameters

| Parameter | Default | Description |
|---|---|---|
| `maxDepth` | `-1` (unlimited) | Maximum depth to traverse |
| `minDepth` | `0` | Minimum depth at which results are included |

!!! info "AWS request cost"
    Each level of the walk issues one `ListObjects` request per prefix visited.
    A walk over a directory with 5 subdirectories issues at least 6 requests
    (1 for the root + 1 per subdirectory). Use `maxDepth` to limit this.
    `minDepth` does **not** reduce requests — all levels are still traversed,
    but shallow results are filtered out before being returned.

See [Walking & Listing](walking-and-listing.md) for a detailed walkthrough.

## @Prefix

Narrows a listing or walk to only S3 keys that begin with the given prefix.
The value is relative — it is appended to the current proxy's position in
the key hierarchy. The prefix is sent server-side in the `ListObjects` request,
so non-matching keys are never returned by AWS.

```java
public interface Repository extends S3.Dir {
    // Only list artifacts under org/apache
    @Prefix("org/apache")
    Stream<S3File> apacheArtifacts();

    // Walk only the org/ namespace
    @Walk
    @Prefix("org/")
    Stream<S3File> orgArtifacts();
}
```

### Partial prefixes

The prefix doesn't need to end with a delimiter. It can be any key prefix,
including a partial name:

```java
public interface Tomcat extends S3.Dir {
    // Only version directories starting with "10."
    // Matches 10.1.18/, 10.1.19/, etc.
    // Skips 9.0.85/, 11.0.0/, etc.
    @Prefix("10.")
    Stream<S3File> version10();
}
```

Because the prefix is applied server-side, a directory with hundreds of entries
can be efficiently narrowed to just the relevant subset.

## @Suffix

Filters results by file name ending. A shorthand for the common case of
filtering by file extension. Multiple values are OR'd.

```java
public interface Assets extends S3.Dir {
    @Suffix(".css")
    Stream<S3File> stylesheets();

    @Suffix({".jpg", ".png", ".gif"})
    Stream<S3File> images();
}
```

`@Suffix` is repeatable with an `exclude` flag. Use `exclude = true` to
remove entries matching a suffix:

```java
public interface Assets extends S3.Dir {
    // All .jar files except sources and javadoc jars
    @Suffix(".jar")
    @Suffix(value = {"-sources.jar", "-javadoc.jar"}, exclude = true)
    Stream<S3File> binaryJars();
}
```

### Type-level suffix

`@Suffix` can also be placed on an interface. It applies to all methods that
return that type:

```java
@Suffix(".parquet")
public interface ParquetFile extends S3.File {
    // Every listing that returns Stream<ParquetFile> will
    // automatically filter to .parquet files only
}
```

## @Match

Filters results by a regular expression on the file name. The entire name
must match (implied `^` and `$`). Uses `Pattern.asMatchPredicate()`.

```java
public interface Reports extends S3.Dir {
    @Match("daily-\\d{4}-\\d{2}-\\d{2}\\.csv")
    Stream<S3File> dailyReports();

    @Match(".*\\.(jpg|png)")
    Stream<S3File> images();
}
```

`@Match` is repeatable with an `exclude` flag. Use `exclude = true` to
remove entries matching a pattern:

```java
public interface Assets extends S3.Dir {
    // All CSS files except reset.css
    @Match(".*\\.css")
    @Match(value = "reset\\.css", exclude = true)
    Stream<S3File> cssExceptReset();
}
```

`@Match` can also be placed on an interface for type-level filtering.

## @Filter

Applies an arbitrary client-side predicate to filter results. The annotation
takes a `Predicate<S3File>` class that must have a no-arg constructor.

```java
public class IsJar implements Predicate<S3File> {
    @Override
    public boolean test(S3File file) {
        return file.getName().endsWith(".jar");
    }
}

public interface VersionDir extends S3.Dir {
    @Filter(IsJar.class)
    Stream<S3File> jars();
}
```

### Type-level filters

`@Filter` can also be placed on an interface. It applies to all methods that
return that type:

```java
@Filter(IsJar.class)
public interface JarFile extends S3.File {
    // Every listing that returns Stream<JarFile> will
    // automatically filter to .jar files only
}

public interface VersionDir extends S3.Dir {
    Stream<JarFile> artifacts();  // only .jar files
}
```

`@Filter` is repeatable — multiple filters are combined with AND logic.

### Filter evaluation order

When multiple filter annotations are present, they are applied in order —
simplest first, most complex last:

1. **@Prefix** — server-side
2. **@Suffix** includes — `String.endsWith()`
3. **@Suffix** excludes
4. **@Match** includes — compiled regex
5. **@Match** excludes
6. **@Filter** — arbitrary `Predicate<S3File>`

Each filter only sees entries that passed the previous ones. Interface-level
filters run before method-level filters within each category.

!!! tip
    Use `@Suffix` for extension checks and `@Match` for name patterns.
    Reserve `@Filter` for cases that need access to the full `S3File`.
    Prefer `@Prefix` over all of these when possible — it filters
    server-side and reduces the data transferred from AWS.

## @Delimiter

Sets the delimiter for the `ListObjects` request. Defaults to `"/"` for
`@Walk` methods. Can be used on both flat listing and walk methods.

```java
public interface Logs extends S3.Dir {
    @Delimiter("-")
    Stream<S3File> byDate();
}
```

## @Marker

Sets the starting position for a listing. Keys before the marker are skipped.
This is the low-level pagination mechanism used by S3.

```java
public interface Archive extends S3.Dir {
    @Marker("2024-01-01")
    Stream<S3File> since2024();
}
```

!!! note
    In most cases, JAWS handles pagination automatically. `@Marker` is
    useful when you need to start a listing from a known position.
