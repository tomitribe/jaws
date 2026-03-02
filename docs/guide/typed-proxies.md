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

## Return Type Dispatch

### Single child — interface return type

A no-arg method returning an interface wraps the child `S3File` as a proxy of that type:

```java
public interface Module extends S3.Dir {
    Source src();        // resolves to child key "src"
    Target target();    // resolves to child key "target"
}
```

The child key is derived from the method name. The child may or may not exist in S3 —
the proxy is created either way.

### Named child — interface return type with String parameter

A method taking a `String` and returning an interface looks up the named child:

```java
public interface Modules extends S3.Dir {
    Module module(String name);  // resolves to child key {name}
}

// Usage
Module core = modules.module("core");
```

### Single file — S3File return type

A no-arg method returning `S3File` returns the child file directly:

```java
public interface Module extends S3.Dir {
    S3File readme();    // returns S3File for "readme"
}
```

If the method declares `throws FileNotFoundException`, the proxy will throw if
the file does not exist in S3.

### Collections — Stream, List, Set, array

Methods returning `Stream<T>`, `List<T>`, `Set<T>`, `Collection<T>`, or `T[]`
produce listings. The element type `T` controls behavior:

| Element type | Behavior |
|---|---|
| `S3File` | Returns raw S3Files from listing |
| Interface extending `S3.Dir` | Delimiter-based listing, directories only |
| Interface extending `S3.File` | Delimiter-based listing, files only |
| Other interface extending `S3` | Flat listing, each entry wrapped as proxy |

```java
public interface Catalog extends S3.Dir {
    Stream<Category> categories();     // Category extends S3.Dir → dirs only
    List<Product> allProducts();       // Product extends S3.File → files only
    S3File[] everything();             // raw S3Files
}
```

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
