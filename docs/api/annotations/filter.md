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
    When possible, use `@Prefix` instead. `@Prefix` filters server-side,
    reducing the HTTP payload from AWS. Use `@Filter` for criteria that
    can't be expressed as a key prefix (e.g., file extension, size, name pattern).

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

### Filter ordering

Interface-level filters run before method-level filters. Both must pass
for a result to be included:

```java
@Filter(IsJar.class)              // 1st: must be .jar
public interface JarFile extends S3.File {}

public interface VersionDir extends S3.Dir {
    @Filter(IsSnapshot.class)     // 2nd: must also be SNAPSHOT
    Stream<JarFile> snapshotJars();
}
```

### Combining with @Prefix

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

## See Also

- [@Prefix](prefix.md) — server-side filtering
- [Filtering](../../guide/filtering.md) — detailed guide on filtering strategies
