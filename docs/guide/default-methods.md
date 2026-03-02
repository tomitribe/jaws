# Default Methods

Java default methods turn JAWS proxy interfaces from passive data mappings
into rich domain objects. Because JAWS invokes default methods normally,
you can layer business logic — serialization, validation, transformation —
directly onto the interface that models your S3 structure.

## Basic Example

The simplest use is adding a convenience method to a file interface:

```java
public interface Config extends S3.File {
    default Properties asProperties() {
        Properties p = new Properties();
        p.load(new StringReader(getValueAsString()));
        return p;
    }
}
```

Callers get a `Properties` object without knowing anything about S3:

```java
Config config = bucket.getFile("app.properties").as(Config.class);
Properties props = config.asProperties();
```

## Building Reusable Base Interfaces

Default methods become truly powerful when you build reusable base
interfaces that sub-interfaces specialize with type information.

### JSON with Jakarta JSON-B

Consider a base interface that handles JSON marshaling:

```java
public interface JsonbFile extends S3.File {

    default <T> T fromJson(Class<T> type) {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            return jsonb.fromJson(getValueAsString(), type);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    default <T> void toJson(T object) {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            setValueAsString(jsonb.toJson(object));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
```

Sub-interfaces supply the type and expose clean `read()` / `write()` methods:

```java
public interface UserFile extends JsonbFile {
    default User read() {
        return fromJson(User.class);
    }

    default void write(User user) {
        toJson(user);
    }
}

public interface OrderFile extends JsonbFile {
    default Order read() {
        return fromJson(Order.class);
    }

    default void write(Order order) {
        toJson(order);
    }
}
```

Now a directory interface can expose fully typed accessors:

```java
public interface UserDir extends S3.Dir {
    Stream<UserFile> users();
    UserFile user(String id);
}
```

```java
UserDir dir = bucket.getFile("users").as(UserDir.class);

// Read
User alice = dir.user("alice.json").read();

// Write
dir.user("bob.json").write(new User("bob", "bob@example.com"));

// Stream all users
dir.users()
    .map(UserFile::read)
    .filter(u -> u.isActive())
    .forEach(System.out::println);
```

The JSON-B boilerplate lives in one place. Every JSON-backed file in
your bucket gets serialization for free by extending `JsonbFile`.

### YAML with SnakeYAML

The same pattern works for any format:

```java
public interface YamlFile extends S3.File {

    default <T> T fromYaml(Class<T> type) {
        Yaml yaml = new Yaml(new Constructor(type, new LoaderOptions()));
        return yaml.load(getValueAsString());
    }

    default <T> void toYaml(T object) {
        Yaml yaml = new Yaml();
        setValueAsString(yaml.dump(object));
    }
}

public interface PipelineFile extends YamlFile {
    default Pipeline read() {
        return fromYaml(Pipeline.class);
    }

    default void write(Pipeline pipeline) {
        toYaml(pipeline);
    }
}
```

### CSV

```java
public interface CsvFile extends S3.File {

    default List<String[]> readCsv() {
        try (CSVReader reader = new CSVReader(
                new StringReader(getValueAsString()))) {
            return reader.readAll();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    default List<String[]> readCsvSkipHeader() {
        List<String[]> rows = readCsv();
        return rows.subList(1, rows.size());
    }
}
```

## Computed Properties

Default methods work well for deriving information from file names or
content without extra S3 calls:

```java
public interface Artifact extends S3.File {

    default String artifactId() {
        // maven-core-3.9.6.jar → maven-core
        String name = file().getName();
        return name.substring(0, name.lastIndexOf('-'));
    }

    default String version() {
        // maven-core-3.9.6.jar → 3.9.6
        String name = file().getName();
        String noExt = name.substring(0, name.lastIndexOf('.'));
        return noExt.substring(noExt.lastIndexOf('-') + 1);
    }

    default String extension() {
        String name = file().getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }
}
```

## Combining with Annotations

Default methods compose naturally with JAWS annotations. The annotations
control *which* files are listed; the default methods control *what you
can do* with them:

```java
@Suffix(".json")
public interface ConfigFile extends JsonbFile {
    default AppConfig read() {
        return fromJson(AppConfig.class);
    }

    default void write(AppConfig config) {
        toJson(config);
    }
}

public interface ConfigDir extends S3.Dir {
    // @Suffix(".json") on ConfigFile filters the listing
    // default read()/write() methods give typed access
    Stream<ConfigFile> configs();

    ConfigFile config(String name);
}
```

## Tips

- **Keep base interfaces generic.** `JsonbFile`, `YamlFile`, and `CsvFile`
  should not know about specific domain types — that is the job of
  sub-interfaces.
- **Use `file()` and `parent()` freely.** Default methods have access to
  all of `S3.File`'s inherited methods, so you can inspect names, sizes,
  metadata, and navigate the key hierarchy.
- **Combine multiple base interfaces.** A file interface can extend both
  `S3.File` and implement a custom mixin interface if your defaults don't
  need `S3.File` methods.
