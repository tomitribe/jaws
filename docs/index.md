# JAWS — Java AWS S3

**Strongly-typed, proxy-based Java abstraction for Amazon S3.**

JAWS lets you define plain Java interfaces that map directly to your S3 bucket structure.
The library creates dynamic proxies that translate method calls into efficient S3 API
operations — no boilerplate, no manual key construction, no pagination code.

## Why JAWS?

Working with the AWS S3 SDK directly means writing repetitive code to build requests,
handle pagination, and assemble key paths. JAWS replaces all of that with a clean
interface-driven model.

Consider a Maven repository hosted in S3:

```text
repository/
  org/
    apache/
      maven/
        maven-core/
          3.9.6/
            maven-core-3.9.6.jar
            maven-core-3.9.6.pom
            maven-core-3.9.6-sources.jar
          4.0.0/
            maven-core-4.0.0.jar
            maven-core-4.0.0.pom
        maven-model/
          3.9.6/
            maven-model-3.9.6.jar
            maven-model-3.9.6.pom
      tomee/
        openejb-core/
          10.0.0/
            openejb-core-10.0.0.jar
            openejb-core-10.0.0.pom
    tomitribe/
      tomitribe-util/
        1.0/
          tomitribe-util-1.0.jar
          tomitribe-util-1.0.pom
```

With JAWS, you define interfaces that mirror this structure — each directory level
becomes a typed interface, each file becomes an `S3.File` or  `S3.Dir`:

```java
import org.tomitribe.jaws.S3;

public interface Repository extends S3.Dir {
    Stream<GroupId> groups();
    GroupId group(String name);
}

public interface GroupId extends S3.Dir {
    Stream<GroupId> groups();       // sub-groups: org/ → apache/ → maven/
    Stream<ArtifactId> artifacts();
    GroupId group(String name);
    ArtifactId artifact(String name);
}

public interface ArtifactId extends S3.Dir {
    Stream<Version> versions();
    Version version(String name);
}

public interface Version extends S3.Dir {
    Stream<Artifact> artifacts();
}

public interface Artifact extends S3.File {
    // inherits getValueAsString(), getSize(), getLastModified(), etc.
}
```

Now navigate the repository like a typed filesystem:

```java
import software.amazon.awssdk.services.s3.S3AsyncClient;
import org.tomitribe.jaws.S3Client;
import org.tomitribe.jaws.S3Bucket;
        
S3Client s3 = new S3Client(S3AsyncClient.builder().build());
S3Bucket bucket = s3.getBucket("repository");
Repository repo = bucket.as(Repository.class);

// Browse to a specific version
Version v = repo.group("org")
    .group("apache")
    .group("maven")
    .artifact("maven-core")
    .version("3.9.6");

// List all artifacts in that version
v.artifacts().forEach(artifact -> {
    System.out.println(artifact.file().getName()
        + " (" + artifact.getSize() + " bytes)");
});
// maven-core-3.9.6.jar (3412507 bytes)
// maven-core-3.9.6.pom (12841 bytes)
// maven-core-3.9.6-sources.jar (1987654 bytes)

// Stream every artifact under org/apache/maven
repo.group("org/apache/maven").artifacts().forEach(artifactId -> {
    System.out.println(artifactId.file().getName());
    artifactId.versions().forEach(version -> {
        System.out.println("  " + version.file().getName());
    });
});
// maven-core
//   3.9.6
//   4.0.0
// maven-model
//   3.9.6
// openejb-core
//   10.0.0
```

## Key Features

- **Typed proxies** — Define interfaces, get proxies. Method names map to S3 keys.
- **Directory & File types** — `S3.Dir` for containers, `S3.File` for objects with content.
  Return types drive listing behavior automatically.
- **Annotations** — `@Name`, `@Parent`, `@Walk`, `@Prefix`, `@Filter`, `@Delimiter`
  give fine-grained control over key resolution, traversal, and filtering.
- **Efficient S3 usage** — Delimiter-based listings, server-side prefix filtering, and
  controlled walk depth minimize AWS API requests.
- **Upload & Download** — Built-in support via the S3 Transfer Manager with progress tracking.

## Quick Start

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>org.tomitribe</groupId>
    <artifactId>jaws-s3</artifactId>
    <version>2.0</version>
</dependency>
```

Then head to [Getting Started](getting-started.md) for a complete walkthrough.
