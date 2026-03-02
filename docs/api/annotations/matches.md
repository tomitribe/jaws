# @Matches

Filters listing results to entries whose name matches a regular expression.

## Declaration

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Matches {
    String value();
}
```

## Attributes

| Attribute | Type | Description |
|---|---|---|
| `value` | `String` | A regular expression matched against `S3File.getName()` |

## Description

Filters results on the client side using `Pattern.compile(value).asMatchPredicate()`.
The **entire** file name must match the regex (implied `^` and `$`) — this is a
full match, not a substring search.

`@Matches` can be placed on methods or on interface types. It is applied
**after** `@Suffix` and **before** `@Filter` in the evaluation order.

Use `@Matches` when the pattern is more complex than a simple suffix. For
suffix-only filtering, prefer `@Suffix` — it is cheaper and easier to read.

## Examples

### Basic regex

```java
public interface Reports extends S3.Dir {
    // Match files like daily-2025-01-15.csv, daily-2025-02-01.csv
    @Matches("daily-\\d{4}-\\d{2}-\\d{2}\\.csv")
    Stream<S3File> dailyReports();
}
```

### Alternation

Use regex alternation to match multiple extensions:

```java
public interface Assets extends S3.Dir {
    @Matches(".*\\.(jpg|png|gif|webp)")
    Stream<S3File> images();
}
```

### Full-match semantics

The regex must match the **entire** file name. A partial pattern will not
match:

```java
public interface Assets extends S3.Dir {
    @Matches("css")          // matches nothing — no file is named exactly "css"
    Stream<S3File> broken();

    @Matches(".*\\.css")     // matches main.css, reset.css, etc.
    Stream<S3File> correct();
}
```

### Type-level matches

When `@Matches` is placed on an interface, it applies automatically to
every listing method that returns that type:

```java
@Matches("\\d{4}-\\d{2}-\\d{2}\\.csv")
public interface DailyReport extends S3.File {
    // Any Stream<DailyReport> listing will only include
    // files matching the date-based CSV pattern
}

public interface Reports extends S3.Dir {
    Stream<DailyReport> reports();   // automatically filtered
    Stream<S3File> everything();     // no filter applied
}
```

### Combining with other annotations

`@Matches` can be combined with `@Prefix`, `@Suffix`, and `@Filter`.
They are evaluated in order — each filter only sees entries that passed
the previous one:

```java
public interface DataDir extends S3.Dir {
    // 1. @Prefix server-side: keys starting with "export-"
    // 2. @Suffix client-side: names ending with ".parquet"
    // 3. @Matches client-side: only 2025 exports
    @Prefix("export-")
    @Suffix(".parquet")
    @Matches("export-2025-.*\\.parquet")
    Stream<S3File> exports2025();
}
```

## Evaluation Order

When multiple filter annotations are present on the same method or type,
they are applied in this order:

1. **@Prefix** — server-side, in the `ListObjects` request
2. **@Suffix** — client-side, `String.endsWith()`
3. **@Matches** — client-side, regex
4. **@Filter** — client-side, arbitrary `Predicate<S3File>`

All client-side filters are AND'd together. A `@Filter` predicate can
safely assume that `@Suffix` and `@Matches` have already passed.

## See Also

- [@Suffix](suffix.md) — client-side suffix filtering (simpler, faster)
- [@Filter](filter.md) — client-side filtering with arbitrary predicates
- [@Prefix](prefix.md) — server-side prefix filtering
- [Filtering](../../guide/filtering.md) — detailed guide on filtering strategies
