# @Suffix

Filters listing results to entries whose name ends with one of the given suffixes.

## Declaration

```java
@Repeatable(Suffixes.class)
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Suffix {
    String[] value();
    boolean exclude() default false;
}
```

## Attributes

| Attribute | Type | Default | Description |
|---|---|---|---|
| `value` | `String[]` | — | One or more suffixes to match against `S3File.getName()` |
| `exclude` | `boolean` | `false` | When `true`, entries matching any suffix are **excluded** instead of included |

## Description

Filters results on the client side using `String.endsWith()`. When multiple
suffixes are given, they are OR'd — a file matches if its name ends with
**any** of them.

`@Suffix` can be placed on methods or on interface types. It is applied
**before** `@Match` and `@Filter` in the evaluation order.

`@Suffix` is repeatable. Include annotations (default) are applied first,
then exclude annotations carve out exceptions. This lets you select a broad
set and remove specific entries:

```java
// All .jar files except sources and javadoc jars
@Suffix(".jar")
@Suffix(value = {"-sources.jar", "-javadoc.jar"}, exclude = true)
Stream<S3File> binaryJars();
```

This is a shorthand for the common case of filtering by file extension.
For more complex name patterns, use `@Match`. For arbitrary criteria,
use `@Filter`.

## Examples

### Single suffix

```java
public interface Assets extends S3.Dir {
    @Suffix(".css")
    Stream<S3File> stylesheets();

    @Suffix(".js")
    Stream<S3File> scripts();
}
```

### Multiple suffixes (OR)

Multiple values match if the name ends with any of them:

```java
public interface Assets extends S3.Dir {
    @Suffix({".jpg", ".png", ".gif"})
    Stream<S3File> images();

    @Suffix({".html", ".htm"})
    Stream<S3File> pages();
}
```

### Exclude

Use `exclude = true` to remove entries that match a suffix:

```java
public interface Assets extends S3.Dir {
    // Everything except JavaScript files
    @Suffix(value = ".js", exclude = true)
    Stream<S3File> noJavaScript();
}
```

### Include + Exclude

Combine include and exclude annotations on the same method:

```java
public interface Assets extends S3.Dir {
    // All CSS files except reset.css
    @Suffix(".css")
    @Suffix(value = "reset.css", exclude = true)
    Stream<S3File> cssExceptReset();

    // All .jar files except sources and javadoc jars
    @Suffix(".jar")
    @Suffix(value = {"-sources.jar", "-javadoc.jar"}, exclude = true)
    Stream<S3File> binaryJars();
}
```

### Type-level suffix

When `@Suffix` is placed on an interface, it applies automatically to
every listing method that returns that type:

```java
@Suffix(".parquet")
public interface ParquetFile extends S3.File {
    // Any Stream<ParquetFile> listing will only include .parquet files
}

public interface DataDir extends S3.Dir {
    Stream<ParquetFile> data();    // automatically filtered to .parquet files
    Stream<S3File> everything();   // no filter applied
}
```

### Combining with other annotations

`@Suffix` can be combined with `@Prefix`, `@Match`, and `@Filter`.
They are evaluated in order — each filter only sees entries that passed
the previous one:

```java
public interface LogDir extends S3.Dir {
    // 1. @Prefix server-side: keys starting with "app-"
    // 2. @Suffix client-side: names ending with ".log"
    @Prefix("app-")
    @Suffix(".log")
    Stream<S3File> appLogs();
}
```

## Evaluation Order

When multiple filter annotations are present on the same method or type,
they are applied in this order:

1. **@Prefix** — server-side, in the `ListObjects` request
2. **@Suffix** includes — client-side, `String.endsWith()`
3. **@Suffix** excludes
4. **@Match** includes — client-side, regex
5. **@Match** excludes
6. **@Filter** — client-side, arbitrary `Predicate<S3File>`

All client-side filters are AND'd together. A `@Filter` predicate can
safely assume that `@Suffix` and `@Match` have already passed.

## See Also

- [@Match](match.md) — client-side regex filtering
- [@Filter](filter.md) — client-side filtering with arbitrary predicates
- [@Prefix](prefix.md) — server-side prefix filtering
- [Filtering](../../guide/filtering.md) — detailed guide on filtering strategies
