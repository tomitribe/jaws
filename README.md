# JAWS — Java AWS S3

Strongly-typed, proxy-based Java abstraction for Amazon S3. Define plain Java
interfaces that mirror your bucket structure and let JAWS translate method calls
into efficient S3 API operations — no boilerplate, no manual key construction,
no pagination code.

## Example

```java
public interface Repository extends S3.Dir {
    Stream<GroupId> groups();
    GroupId group(String name);
}

public interface GroupId extends S3.Dir {
    Stream<ArtifactId> artifacts();
    ArtifactId artifact(String name);
}

public interface ArtifactId extends S3.Dir {
    Stream<Version> versions();
}

public interface Version extends S3.Dir {
    @Suffix(".jar")
    Stream<S3File> jars();

    @Suffix(".jar")
    @Suffix(value = {"-sources.jar", "-javadoc.jar"}, exclude = true)
    Stream<S3File> binaryJars();
}
```

```java
S3Client s3 = new S3Client(S3AsyncClient.builder().build());
Repository repo = s3.getBucket("repository").as(Repository.class);

repo.group("org/apache/maven")
    .artifact("maven-core")
    .versions()
    .forEach(v -> System.out.println(v.file().getName()));
```

## Documentation

Full documentation is available at
[tomitribe.github.io/jaws](https://tomitribe.github.io/jaws/).
