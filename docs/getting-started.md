# Getting Started

## Prerequisites

- Java 11 or later

## Installation

Add JAWS to your Maven project:

```xml
<dependency>
    <groupId>org.tomitribe</groupId>
    <artifactId>jaws-s3</artifactId>
    <version>2.0.5</version>
</dependency>
```

## Creating an S3Client

The `S3Client` is the entry point. It wraps an AWS `S3AsyncClient`:

```java
import org.tomitribe.jaws.s3.S3Client;
import software.amazon.awssdk.services.s3.S3AsyncClient;

S3AsyncClient asyncClient = S3AsyncClient.builder().build();
S3Client s3 = new S3Client(asyncClient);
```

## Working with Buckets

Get a reference to an existing bucket:

```java
S3Bucket bucket = s3.getBucket("my-bucket");
```

Or create a new one:

```java
S3Bucket bucket = s3.createBucket("my-new-bucket");
```

List all buckets:

```java
s3.buckets().forEach(b -> System.out.println(b.getName()));
```

## Defining Your First Proxy

Imagine a bucket with this structure:

```text
my-bucket/
  config.properties
  users/
    alice.json
    bob.json
```

Define interfaces that mirror this structure:

```java
public interface UserRepository extends S3.Dir {
    @Name("config.properties")
    S3File config();

    Users users();
}

public interface Users extends S3.Dir {
    Stream<UserFile> users();

    UserFile user(String name);
}

@Match(".*\\.json")
public interface UserFile extends S3.File {
    default String getUserName() {
        return file().getName().replaceAll("\\.json$", "");
    }
}
```

The `@Match(".*\\.json")` annotation does double duty: listing methods like
`Stream<UserFile> users()` will only return `.json` files, and single-arg
methods like `UserFile user(String name)` will reject names that don't match
the pattern (throwing `IllegalArgumentException`).

Create and use the proxy:

```java
S3Bucket bucket = s3.getBucket("my-bucket");
UserRepository root = bucket.as(UserRepository.class);

// Read config
String config = root.config().getValueAsString();

// List users
root.users().users().forEach(user ->
    System.out.println(user.getUserName())
);

// Get a specific user — name is validated against @Match
UserFile alice = root.users().user("alice.json");
String json = alice.getValueAsString();
```

## Testing

JAWS ships a `jaws-s3-test` module with an in-memory S3 backend powered by
[S3Proxy](https://github.com/gaul/s3proxy). Add it as a test dependency alongside
JUnit 5:

```xml
<dependency>
    <groupId>org.tomitribe</groupId>
    <artifactId>jaws-s3-test</artifactId>
    <version>2.0.5</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter-api</artifactId>
    <version>5.10.2</version>
    <scope>test</scope>
</dependency>
```

Register a `MockS3Extension` in your test. It starts a local S3 server before each
test and tears it down after:

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.tomitribe.jaws.s3.MockS3Extension;
import org.tomitribe.jaws.s3.Name;
import org.tomitribe.jaws.s3.S3;
import org.tomitribe.jaws.s3.S3Asserts;
import org.tomitribe.jaws.s3.S3Client;
import org.tomitribe.jaws.s3.S3File;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GettingStartedTest {

    @RegisterExtension
    private final MockS3Extension mockS3 = new MockS3Extension();

    @Test
    void test() {
        final S3Client s3Client = new S3Client(mockS3.getS3Client());

        final UserRepository root = s3Client.createBucket("my-bucket")
                .put("config.properties", "color=red")
                .put("users/alice.json", "{\"name\":\"Alice\"}")
                .put("users/bob.json", "{\"name\":\"Bob\"}")
                .as(UserRepository.class);

        // Read entries
        assertEquals("color=red", root.config().getValueAsString());
        assertEquals("{\"name\":\"Alice\"}", root.users().user("alice.json").getValueAsString());

        // List entries
        final String names = root.users().users()
                .map(UserFile::getUserName)
                .sorted()
                .collect(Collectors.joining(", "));

        assertEquals("alice, bob", names);

        // Add entry
        root.users().user("charlie.json").setValueAsString("{\"name\":\"Charlie\"}");

        // Review all results
        S3Asserts.of(mockS3.getS3Client(), "my-bucket")
                .snapshot()
                .assertContent("users/charlie.json", "{\"name\":\"Charlie\"}")
                .assertContent("users/alice.json", "{\"name\":\"Alice\"}")
                .assertExists("config.properties")
                .assertNotExists("users/dave.json");
    }

    public interface UserRepository extends S3.Dir {
        @Name("config.properties")
        S3File config();

        Users users();
    }

    public interface Users extends S3.Dir {
        Stream<UserFile> users();

        UserFile user(String name);
    }

    public interface UserFile extends S3.File {
        default String getUserName() {
            return file().getName().replaceAll("\\.json$", "");
        }
    }
}
```

The test creates a bucket, populates it with the fluent `put()` API, then uses a
typed proxy to read, list, and write entries — the same code you'd write against real
S3. `S3Asserts` provides a snapshot-based way to verify the final bucket state
directly against the underlying S3 client.

## Next Steps

- [Typed Proxies](guide/typed-proxies.md) — Learn the full proxy dispatch model
- [Directories & Files](guide/directories-and-files.md) — Understand `S3.Dir` vs `S3.File`
- [Annotations](guide/annotations.md) — Fine-tune key resolution and listing behavior
- [Listing & Recursion](guide/walking-and-listing.md) — Immediate vs recursive listing with `@Recursive`
