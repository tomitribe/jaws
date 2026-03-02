# @Name

Overrides the S3 key segment used when resolving a proxy method to a child object.

## Declaration

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Name {
    String value();
}
```

## Attributes

| Attribute | Type | Description |
|---|---|---|
| `value` | `String` | The S3 key segment to use instead of the method name |

## Description

By default, the proxy handler uses the method name as the S3 key segment when
looking up a child object. `@Name` allows mapping to an S3 key that cannot be
expressed as a valid Java identifier — for example, keys containing dots,
hyphens, or slashes.

## Examples

### Mapping to a dotted filename

```java
public interface Module extends S3.Dir {
    @Name("pom.xml")
    PomFile pomXml();
}
```

Without `@Name`, `pomXml()` would resolve to the key segment `pomXml`.
With `@Name("pom.xml")`, it resolves to `pom.xml`.

### Mapping to a path with slashes

```java
public interface Project extends S3.Dir {
    @Name("src/main/java")
    Sources sources();
}
```

### Mapping to a hyphenated name

```java
public interface Catalog extends S3.Dir {
    @Name("my-config")
    Config myConfig();

    @Name("release-notes.txt")
    ReleaseNotes releaseNotes();
}
```

## See Also

- [Typed Proxies](../../guide/typed-proxies.md) — how method names map to S3 keys
- [@Parent](parent.md) — navigate upward instead of to a child
