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

Applies an arbitrary client-side predicate to filter listing results. The
predicate class is instantiated via its no-arg constructor and applied to
each `S3File` in the listing.

`@Filter` is the most powerful filtering annotation, but also the **last
to run**. When a method or type carries several filter annotations, they
are evaluated in a fixed order — simplest and cheapest first:

1. **@Prefix** — server-side, in the `ListObjects` request
2. **@Suffix** includes — client-side, `String.endsWith()`
3. **@Suffix** excludes
4. **@Match** includes — client-side, compiled regex
5. **@Match** excludes
6. **@Filter** — client-side, arbitrary `Predicate<S3File>`

Because `@Filter` runs last, the predicate will only see entries that have
already passed `@Prefix`, `@Suffix`, and `@Match`. This is intentional —
a predicate can safely assume, for example, that the file name ends with
`.jar` if `@Suffix(".jar")` is also present on the same method or type.

`@Filter` is repeatable — multiple filters on the same method are combined
with AND logic. It can be placed on methods or on interface types.
Interface-level filters run before method-level filters.

!!! tip
    When possible, use a simpler annotation. `@Prefix` filters server-side,
    reducing the HTTP payload from AWS. `@Suffix` handles extension checks
    and `@Match` handles name patterns. Reserve `@Filter` for criteria
    that need access to the full `S3File` — size, metadata, path components,
    or other attributes beyond the name.

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

### What your predicate sees

Because `@Prefix`, `@Suffix`, and `@Match` all run before `@Filter`,
you can write predicates that depend on those earlier filters having
already narrowed the results:

```java
// This predicate can safely assume every file ends with ".jar"
// because @Suffix(".jar") already ran.  It strips the suffix
// and inspects what remains.
public class IsSnapshot implements Predicate<S3File> {
    @Override
    public boolean test(S3File file) {
        String name = file.getName();                     // e.g. "commons-lang-3.14-SNAPSHOT.jar"
        String base = name.substring(0, name.length() - 4); // strip ".jar"
        return base.endsWith("-SNAPSHOT");
    }
}

@Suffix(".jar")
public interface JarFile extends S3.File {}

public interface VersionDir extends S3.Dir {
    // @Suffix(".jar") on JarFile runs first
    // @Filter(IsSnapshot.class) sees only .jar files
    @Filter(IsSnapshot.class)
    Stream<JarFile> snapshotJars();
}
```

### Input validation on single-arg methods

When `@Filter` is placed on the return type of a single-arg proxy method,
it validates the input name. If the `S3File` doesn't pass the predicate,
an `IllegalArgumentException` is thrown:

```java
@Filter(IsJar.class)
public interface JarFile extends S3.File {}

public interface VersionDir extends S3.Dir {
    Stream<JarFile> artifacts();         // lists only .jar files
    JarFile artifact(String name);       // validates name passes IsJar
}

versionDir.artifact("commons.jar");      // OK
versionDir.artifact("readme.txt");       // throws IllegalArgumentException
```

This ensures that names passed to single-arg methods are consistent with
the filtering applied to listing methods that return the same type.

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
- [@Match](match.md) — client-side regex filtering
- [@Prefix](prefix.md) — server-side prefix filtering
- [Filtering](../../guide/filtering.md) — detailed guide on filtering strategies
