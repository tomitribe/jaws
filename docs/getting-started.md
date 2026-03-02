# Getting Started

## Prerequisites

- Java 11 or later
- An AWS account with S3 access
- AWS credentials configured (environment variables, `~/.aws/credentials`, or IAM role)

## Installation

Add JAWS to your Maven project:

```xml
<dependency>
    <groupId>org.tomitribe</groupId>
    <artifactId>jaws-s3</artifactId>
    <version>2.0-SNAPSHOT</version>
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
public interface MyBucket extends S3.Dir {
    @Name("config.properties")
    Config config();

    Users users();
}

public interface Config extends S3.File {
    // Inherits getValueAsString(), setValueAsString(), etc.
}

public interface Users extends S3.Dir {
    Stream<S3File> list();

    S3File file(String name);
}
```

Create and use the proxy:

```java
S3Bucket bucket = s3.getBucket("my-bucket");
MyBucket root = bucket.as(MyBucket.class);

// Read config
String config = root.config().getValueAsString();

// List users
root.users().list().forEach(user ->
    System.out.println(user.getName())
);

// Get a specific user
S3File alice = root.users().file("alice.json");
String json = alice.getValueAsString();
```

## Next Steps

- [Typed Proxies](guide/typed-proxies.md) — Learn the full proxy dispatch model
- [Directories & Files](guide/directories-and-files.md) — Understand `S3.Dir` vs `S3.File`
- [Annotations](guide/annotations.md) — Fine-tune key resolution and listing behavior
- [Walking & Listing](guide/walking-and-listing.md) — Recursive traversal with `@Walk`
