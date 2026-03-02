# Container Registry

This example models an OCI/Docker container image registry stored in S3.
It demonstrates deep interface chaining, `@Name` for underscore-prefixed keys,
`getValueAsStream()` for binary data, and default methods with domain logic.

## S3 Bucket Structure

```text
v2/
  repositories/
    myorg/
      api-server/
        _manifests/
          tags/
            latest/
              current/
                link
            v1.0.0/
              current/
                link
            v1.1.0/
              current/
                link
          revisions/
            sha256/
              abc123.../
                link
        _layers/
          sha256/
            def456.../
              link
            789abc.../
              link
      web-frontend/
        _manifests/
          tags/
            latest/
              current/
                link
        _layers/
          sha256/
            aaa111.../
              link
    blobs/
      sha256/
        abc123.../
          data
        def456.../
          data
        789abc.../
          data
```

## Interfaces

```java
// Registry root — the v2/ prefix
public interface Registry extends S3.Dir {
    Repositories repositories();
    BlobStore blobs();
}

// The repositories/ directory
public interface Repositories extends S3.Dir {
    // Navigate to a namespace (e.g., "myorg")
    Namespace namespace(String name);
    Stream<Namespace> namespaces();
}

// A namespace (organization or user)
public interface Namespace extends S3.Dir {
    ImageRepo image(String name);
    Stream<ImageRepo> images();
}

// A single image repository (e.g., api-server)
public interface ImageRepo extends S3.Dir {

    // @Name handles the underscore-prefixed directory names
    @Name("_manifests")
    Manifests manifests();

    @Name("_layers")
    Layers layers();
}

// The _manifests/ directory
public interface Manifests extends S3.Dir {
    Tags tags();
    Revisions revisions();
}

// The tags/ directory — lists all available tags
public interface Tags extends S3.Dir {
    Tag tag(String name);
    Stream<Tag> tags();
}

// A single tag (e.g., "latest", "v1.0.0")
public interface Tag extends S3.Dir {
    Current current();
}

// The current/ subdirectory under a tag
public interface Current extends S3.Dir {
    LinkFile link();
}

// The revisions/ directory
public interface Revisions extends S3.Dir {
    DigestDir sha256();
}

// The _layers/ directory
public interface Layers extends S3.Dir {
    DigestDir sha256();
}

// A sha256/ directory containing digest-named subdirectories
public interface DigestDir extends S3.Dir {
    DigestEntry digest(String hash);
    Stream<DigestEntry> digests();
}

// A single digest entry directory
public interface DigestEntry extends S3.Dir {
    LinkFile link();
}

// A link file — contains a digest reference as text
public interface LinkFile extends S3.File {

    // Parse the digest algorithm from the link content
    default String getAlgorithm() {
        String content = getValueAsString().trim();
        return content.substring(0, content.indexOf(':'));
    }

    // Parse the digest hash from the link content
    default String getDigest() {
        String content = getValueAsString().trim();
        return content.substring(content.indexOf(':') + 1);
    }
}

// The blobs/ directory at the registry root
public interface BlobStore extends S3.Dir {
    DigestDir sha256();
}

// A blob's data file — binary content
public interface BlobData extends S3.File {

    default String getContentType() {
        return getObjectMetadata().contentType();
    }

    default boolean isManifest() {
        String ct = getContentType();
        return ct != null && ct.contains("manifest");
    }
}
```

## Usage

### Resolve an image tag to its digest

Navigate through the deep interface chain to read the digest a tag
points to:

```java
Registry registry = bucket.as(Registry.class);

ImageRepo apiServer = registry.repositories()
    .namespace("myorg")
    .image("api-server");

// Follow the chain: _manifests → tags → v1.0.0 → current → link
LinkFile link = apiServer.manifests()
    .tags()
    .tag("v1.0.0")
    .current()
    .link();

System.out.println(link.getAlgorithm());
// Output: sha256

System.out.println(link.getDigest());
// Output: abc123...
```

### List all tags for an image

```java
apiServer.manifests().tags().tags().forEach(tag -> {
    System.out.println(tag.file().getName());
});
// Output:
//   latest
//   v1.0.0
//   v1.1.0
```

### Access binary blob data

`getValueAsStream()` returns the raw bytes for binary objects like
container layers:

```java
BlobStore blobs = registry.blobs();

DigestEntry entry = blobs.sha256().digest("def456...");
LinkFile blobLink = entry.link();
System.out.println(blobLink.getValueAsString().trim());
// Output: sha256:def456...

// Read binary blob content as a stream
BlobData blob = blobs.sha256().digest("abc123...").data();

try (InputStream is = blob.getValueAsStream()) {
    // Process the binary content...
}
```

### Inspect content types

Default methods on `BlobData` add domain logic for identifying blob types:

```java
BlobData blob = registry.blobs().sha256().digest("abc123...").data();

System.out.println(blob.getContentType());
// Output: application/vnd.oci.image.manifest.v1+json

System.out.println(blob.isManifest());
// Output: true
```

## Features Used

- [`@Name`](../api/annotations/name.md) — maps methods to `_manifests`, `_layers`, and other keys that start with underscores
- [Deep interface chaining](../guide/typed-proxies.md#single-child--interface-return-type) — models the nested registry path: `repositories → namespace → image → _manifests → tags → tag → current → link`
- [`getValueAsStream()`](../guide/uploads-and-downloads.md#reading-content) — reads binary blob data
- [Default methods](../guide/typed-proxies.md#default-methods) — `LinkFile.getAlgorithm()`, `LinkFile.getDigest()`, `BlobData.isManifest()` add parsing logic
- [`getObjectMetadata()`](../api/s3-interfaces.md#s3file) — `BlobData.getContentType()` reads the S3 content type

## Test

See [ContainerRegistryTest.java](https://github.com/tomitribe/jaws/blob/master/jaws-s3/src/test/java/org/tomitribe/jaws/s3/examples/ContainerRegistryTest.java) for a working test of this example.
