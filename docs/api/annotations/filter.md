# @Filter

Applies a client-side predicate to filter listing results.

## Declaration

```java
@Repeatable(Filters.class)
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Filter {
    Class<? extends Predicate<S3File>> value();
}
```

## Attributes

| Attribute | Type | Description |
|---|---|---|
| `value` | `Class<? extends Predicate<S3File>>` | The predicate class to use as a filter. Must have a no-arg constructor. |

## Description

Filters results on the client side after data is returned from AWS. The
predicate class is instantiated via its no-arg constructor and applied to
each `S3File` in the listing.

`@Filter` is repeatable — multiple filters on the same method are combined
with AND logic. It can be placed on methods or on interface types.

!!! tip
    When possible, use a simpler annotation. `@Prefix` filters server-side,
    reducing the HTTP payload from AWS. `@Suffix` handles extension checks
    and `@Matches` handles name patterns. Reserve `@Filter` for criteria
    that need access to the full `S3File` (e.g., size, metadata, path
    components).

## Examples

### Method-level filter

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

    // Multiple filters — both must pass (AND logic)
    @Filter(IsJar.class)
    @Filter(IsSnapshot.class)
    Stream<S3File> snapshotJars();
}
```

### Type-level filter

When `@Filter` is placed on an interface, it applies automatically to
every listing method that returns that type:

```java
@Filter(IsJar.class)
public interface JarFile extends S3.File {
    // Any Stream<JarFile> listing will only include .jar files
}

public interface VersionDir extends S3.Dir {
    Stream<JarFile> artifacts();   // automatically filtered to .jar files
    Stream<S3File> everything();   // no filter applied
}
```

### Evaluation order

When multiple filter annotations are present, they are applied in order —
simplest first, most complex last:

1. **@Prefix** — server-side, in the `ListObjects` request
2. **@Suffix** — client-side, `String.endsWith()`
3. **@Matches** — client-side, compiled regex
4. **@Filter** — client-side, arbitrary `Predicate<S3File>`

Interface-level filters run before method-level filters within each
category. All client-side filters are AND'd together. A `@Filter`
predicate can safely assume that `@Suffix` and `@Matches` have already
passed.

```java
@Suffix(".jar")                   // 1st: interface-level suffix
public interface JarFile extends S3.File {}

public interface VersionDir extends S3.Dir {
    @Filter(IsSnapshot.class)     // 2nd: method-level filter (sees only .jar)
    Stream<JarFile> snapshotJars();
}
```

### Combining with other annotations

For maximum efficiency, use `@Prefix` to reduce the result set server-side,
then layer on client-side filters as needed:

```java
public interface Repository extends S3.Dir {
    @Prefix("org/apache")
    @Suffix(".jar")
    Stream<S3File> apacheJars();

    @Prefix("org/apache")
    @Suffix(".jar")
    @Filter(IsSnapshot.class)
    Stream<S3File> apacheSnapshotJars();
}
```

## See Also

- [@Suffix](suffix.md) — client-side suffix filtering (simpler, faster)
- [@Matches](matches.md) — client-side regex filtering
- [@Prefix](prefix.md) — server-side prefix filtering
- [Filtering](../../guide/filtering.md) — detailed guide on filtering strategies
