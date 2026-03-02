# @Parent

Navigates upward in the S3 key hierarchy from the current proxy's position.

## Declaration

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Parent {
    int value() default 1;
}
```

## Attributes

| Attribute | Type | Default | Description |
|---|---|---|---|
| `value` | `int` | `1` | The number of levels to navigate upward |

## Description

Marks a proxy method as navigating upward rather than downward. The annotated
method must return an interface extending `S3`, and the proxy will be created
for the ancestor at the specified depth.

A value of `1` (the default) navigates to the immediate parent. Higher values
skip intermediate levels.

## Examples

### Navigate to immediate parent

```java
public interface Version extends S3.Dir {
    @Parent
    ArtifactDir artifact();
}
```

Given a proxy at `org/apache/maven/maven-core/3.9.6/`, calling `artifact()`
returns a proxy at `org/apache/maven/maven-core/`.

### Navigate multiple levels

```java
public interface Version extends S3.Dir {
    // version → artifact (1 level)
    @Parent
    ArtifactDir artifact();

    // version → artifact → group (2 levels)
    @Parent(2)
    GroupDir group();

    // version → artifact → group → repository root (3 levels)
    @Parent(3)
    Repository repository();
}
```

### Practical use case — breadcrumb navigation

```java
public interface Photo extends S3.File {
    @Parent
    Album album();

    @Parent(2)
    Year year();

    @Parent(3)
    PhotoLibrary library();
}

// Navigate from a deeply nested photo back to its album
Photo photo = library.year("2024").album("vacation").photo("sunset.jpg");
Album album = photo.album();          // one level up
Year year = photo.year();             // two levels up
PhotoLibrary lib = photo.library();   // three levels up
```

## Errors

A `NoParentException` is thrown at runtime if the proxy is already at the
bucket root and no further parent exists.

## See Also

- [Typed Proxies](../../guide/typed-proxies.md) — proxy dispatch model
- [@Name](name.md) — navigate downward to a named child
