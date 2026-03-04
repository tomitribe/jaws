# Typed Proxies

JAWS uses Java's dynamic proxy mechanism to turn user-defined interfaces into live
views of an S3 bucket. Each method call on the proxy translates into the appropriate
S3 operation based on the method's return type, name, and annotations.

## How It Works

When you call `bucket.as(MyInterface.class)` or `s3File.as(MyInterface.class)`,
JAWS creates a `java.lang.reflect.Proxy` backed by an `S3Handler`. The handler
inspects each method invocation and dispatches it based on:

1. **Return type** — determines the kind of operation (lookup, listing, content access)
2. **Method name** — used as the S3 key segment (unless overridden by `@Name`)
3. **Annotations** — fine-tune key resolution, traversal, and filtering

## Method Signatures

The handler recognizes the following method signatures. In the table
below, `keyName` indicates the method name is used as the S3 key
segment, while `anyName` indicates the method name is not significant.
`T` is any user-defined interface.

| Signature | Returns |
|---|---|
| `T keyName()` | proxy for child `keyName` as type `T` |
| `@Name("keyName") T anyName()` | proxy for child `keyName` as type `T` |
| `T anyName(String keyName)` | proxy for child `keyName` as type `T`, validated&sup1; |
| `S3File keyName()` | the `S3File` for child `keyName` |
| `Stream<T> anyName()` | all immediate children as type `T` |
| `Stream<S3.File> anyName()` | all immediate files |
| `Stream<S3.Dir> anyName()` | all immediate directories |
| `List<T> anyName()` | all immediate children as a `List` |
| `Set<T> anyName()` | all immediate children as a `Set` |
| `Collection<T> anyName()` | all immediate children as a `List` |
| `T[] anyName()` | all immediate children as an array |
| `@Parent T anyName()` | proxy for the parent directory |
| `@Parent(n) T anyName()` | proxy for the ancestor `n` levels up |
| `@Prefix("p") Stream<T> anyName()` | immediate children whose key starts with `p` (server-side) |
| `@Suffix(".x") Stream<T> anyName()` | children whose name ends with `.x` |
| `@Match("re") Stream<T> anyName()` | children whose name matches regex `re` |
| `@Filter(F.class) Stream<T> anyName()` | children that pass predicate `F` |
| `@Recursive Stream<T> anyName()` | all descendant files as type `T` |
| `@Recursive Stream<S3.Dir> anyName()` | all descendant directories |

&sup1; When the return type or the method carries filter annotations
(`@Suffix`, `@Match`, `@Prefix`, `@Filter`), the input name is
**validated** before the proxy is created. An `IllegalArgumentException`
is thrown if the name doesn't pass. See
[Filtering — Input Validation](filtering.md#input-validation-on-single-arg-methods).

Filter annotations on listing methods (`@Suffix`, `@Match`, `@Filter`)
apply to all collection return types — `Stream`, `List`, `Set`,
`Collection`, and arrays. Only `Stream<T>` is shown above for brevity.

### Examples

```java
public interface Module extends S3.Dir {
    // T keyName() — child proxy
    Source src();
    Target target();

    // @Name("keyName") T anyName()
    @Name("pom.xml")
    PomFile pomXml();

    // S3File keyName()
    S3File readme();
}

@Match(".*\\.json")
public interface UserFile extends S3.File {}

public interface Users extends S3.Dir {
    // Stream<T> anyName() — immediate listing, filtered by @Match on UserFile
    Stream<UserFile> users();

    // T anyName(String keyName) — validated against @Match on UserFile
    UserFile user(String name);
}

users.user("alice.json");   // OK — matches @Match
users.user("notes.txt");    // throws IllegalArgumentException
```

If a method returning `S3File` declares `throws FileNotFoundException`,
the proxy will throw if the file does not exist in S3.

## The `as()` Method

Proxies can be created at any level:

```java
// From a bucket — proxy for the root
Catalog catalog = bucket.as(Catalog.class);

// From any S3File — proxy for that location
S3File dir = bucket.getFile("some/path");
MyDir view = dir.as(MyDir.class);
```

## Default Methods

Proxy interfaces can include default methods. They are invoked normally:

```java
public interface Config extends S3.File {
    default Properties asProperties() {
        Properties p = new Properties();
        p.load(new StringReader(getValueAsString()));
        return p;
    }
}
```

## Navigation

Every proxy has access to its position in the key hierarchy:

```java
public interface S3 {
    S3File file();      // the S3File backing this proxy
    S3File parent();    // the parent S3File
}
```

For navigation beyond the immediate parent, use the [`@Parent`](annotations.md#parent) annotation.
