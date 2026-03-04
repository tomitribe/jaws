# @Match

Filters listing results to entries whose name matches a regular expression.

## Declaration

```java
@Repeatable(Matches.class)
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Match {
    String value();
    boolean exclude() default false;
}
```

## Attributes

| Attribute | Type | Default | Description |
|---|---|---|---|
| `value` | `String` | — | A regular expression matched against `S3File.getName()` |
| `exclude` | `boolean` | `false` | When `true`, entries matching the pattern are **excluded** instead of included |

## Description

Filters results on the client side using `Pattern.compile(value).asMatchPredicate()`.
The **entire** file name must match the regex (implied `^` and `$`) — this is a
full match, not a substring search.

`@Match` can be placed on methods or on interface types. It is applied
**after** `@Suffix` and **before** `@Filter` in the evaluation order.

`@Match` is repeatable. Include annotations (default) are applied first,
then exclude annotations carve out exceptions. This lets you select a broad
set and remove specific entries:

```java
// All CSS files except reset.css
@Match(".*\\.css")
@Match(value = "reset\\.css", exclude = true)
Stream<S3File> cssExceptReset();
```

Use `@Match` when the pattern is more complex than a simple suffix. For
suffix-only filtering, prefer `@Suffix` — it is cheaper and easier to read.

## Examples

### Basic regex

```java
public interface Reports extends S3.Dir {
    // Match files like daily-2025-01-15.csv, daily-2025-02-01.csv
    @Match("daily-\\d{4}-\\d{2}-\\d{2}\\.csv")
    Stream<S3File> dailyReports();
}
```

### Alternation

Use regex alternation to match multiple extensions:

```java
public interface Assets extends S3.Dir {
    @Match(".*\\.(jpg|png|gif|webp)")
    Stream<S3File> images();
}
```

### Full-match semantics

The regex must match the **entire** file name. A partial pattern will not
match:

```java
public interface Assets extends S3.Dir {
    @Match("css")          // matches nothing — no file is named exactly "css"
    Stream<S3File> broken();

    @Match(".*\\.css")     // matches main.css, reset.css, etc.
    Stream<S3File> correct();
}
```

### Exclude

Use `exclude = true` to remove entries that match a pattern:

```java
public interface Groups extends S3.Dir {
    // All directories except those ending in -alpha
    @Match(value = ".*-alpha", exclude = true)
    Stream<S3.Dir> nonAlpha();
}
```

### Include + Exclude

Combine include and exclude annotations on the same method:

```java
public interface Assets extends S3.Dir {
    // All CSS files except reset.css
    @Match(".*\\.css")
    @Match(value = "reset\\.css", exclude = true)
    Stream<S3File> cssExceptReset();
}
```

### Type-level match

When `@Match` is placed on an interface, it applies automatically to
every listing method that returns that type:

```java
@Match("\\d{4}-\\d{2}-\\d{2}\\.csv")
public interface DailyReport extends S3.File {
    // Any Stream<DailyReport> listing will only include
    // files matching the date-based CSV pattern
}

public interface Reports extends S3.Dir {
    Stream<DailyReport> reports();   // automatically filtered
    Stream<S3File> everything();     // no filter applied
}
```

### Input validation on single-arg methods

When `@Match` is placed on the return type of a single-arg proxy method,
it validates the input name. If the name doesn't match the pattern, an
`IllegalArgumentException` is thrown:

```java
@Match(".*\\.json")
public interface UserFile extends S3.File {}

public interface Users extends S3.Dir {
    Stream<UserFile> users();       // lists only .json files
    UserFile user(String name);     // validates name matches .*\.json
}

users.user("alice.json");     // OK — matches the pattern
users.user("notes.txt");      // throws IllegalArgumentException
```

This ensures that names passed to single-arg methods are consistent with
the filtering applied to listing methods that return the same type.

`@Match` on the method itself also validates:

```java
public interface Reports extends S3.Dir {
    @Match("report-\\d{4}\\.csv")
    S3File report(String name);     // validates name matches the pattern
}
```

### Combining with other annotations

`@Match` can be combined with `@Prefix`, `@Suffix`, and `@Filter`.
They are evaluated in order — each filter only sees entries that passed
the previous one:

```java
public interface DataDir extends S3.Dir {
    // 1. @Prefix server-side: keys starting with "export-"
    // 2. @Suffix client-side: names ending with ".parquet"
    // 3. @Match client-side: only 2025 exports
    @Prefix("export-")
    @Suffix(".parquet")
    @Match("export-2025-.*\\.parquet")
    Stream<S3File> exports2025();
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

- [@Suffix](suffix.md) — client-side suffix filtering (simpler, faster)
- [@Filter](filter.md) — client-side filtering with arbitrary predicates
- [@Prefix](prefix.md) — server-side prefix filtering
- [Filtering](../../guide/filtering.md) — detailed guide on filtering strategies
