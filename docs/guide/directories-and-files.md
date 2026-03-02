# Directories & Files

JAWS distinguishes between S3 directories (prefixes) and S3 objects (files) through
the `S3.Dir` and `S3.File` sub-interfaces. The interface your proxy extends tells JAWS
what operations are available and how listings should behave.

## S3.Dir — Directories

Interfaces that model a folder or container of S3 objects extend `S3.Dir`:

```java
public interface Photos extends S3.Dir {
    Stream<Album> albums();
    Album album(String name);
}
```

`S3.Dir` provides these built-in methods:

| Method | Description |
|---|---|
| `file(String name)` | Returns the child `S3File` with the given name |
| `files()` | All objects under this prefix recursively (flat, no delimiter) |
| `list()` | Immediate children — both files and directories (delimiter-based) |
| `upload(File)` | Uploads a local file using its name as the key |
| `upload(File, TransferListener)` | Upload with progress tracking |

### Return-type dispatch for directories

When a proxy method returns `Stream<X>` where `X extends S3.Dir`, JAWS automatically
uses a delimiter-based listing and returns **only directories** (commonPrefixes):

```java
public interface Repository extends S3.Dir {
    // Catalog extends S3.Dir → only directories are returned
    Stream<Catalog> catalogs();
}
```

This is more efficient than filtering — directories that don't match are never
transferred from AWS.

## S3.File — Files with Content

Interfaces that model a single S3 object with readable/writable content extend `S3.File`:

```java
public interface Config extends S3.File {
    // All these methods are inherited:
    // getValueAsString(), getValueAsStream()
    // setValueAsString(), setValueAsStream(), setValueAsFile()
    // getETag(), getSize(), getLastModified(), getObjectMetadata()
}
```

### Inherited methods

| Method | Description |
|---|---|
| `getValueAsString()` | Read content as a `String` |
| `getValueAsStream()` | Read content as an `InputStream` |
| `setValueAsString(String)` | Write a string as content |
| `setValueAsStream(InputStream)` | Write from a stream |
| `setValueAsFile(File)` | Write from a local file |
| `getETag()` | The object's ETag (typically MD5) |
| `getSize()` | Content length in bytes |
| `getLastModified()` | Last modified timestamp |
| `getObjectMetadata()` | Full metadata including content type |

### Return-type dispatch for files

When a proxy method returns `Stream<X>` where `X extends S3.File`, JAWS returns
**only files** (contents, not commonPrefixes):

```java
public interface Album extends S3.Dir {
    // Photo extends S3.File → only files are returned
    Stream<Photo> photos();
}
```

## Choosing Between Dir and File

| Your interface models... | Extend |
|---|---|
| A folder that contains other things | `S3.Dir` |
| A single object you want to read/write | `S3.File` |
| Something where you only need `file()` and `parent()` | `S3` |

## Example: Maven Repository

A complete example modeling a Maven repository structure:

```java
public interface Repository extends S3.Dir {
    Stream<GroupDir> groups();         // GroupDir extends S3.Dir
}

public interface GroupDir extends S3.Dir {
    Stream<ArtifactDir> artifacts();  // ArtifactDir extends S3.Dir
}

public interface ArtifactDir extends S3.Dir {
    Stream<VersionDir> versions();    // VersionDir extends S3.Dir

    @Name("maven-metadata.xml")
    Metadata metadata();              // Metadata extends S3.File
}

public interface VersionDir extends S3.Dir {
    @Name("pom.xml")
    PomFile pom();

    Stream<Artifact> artifacts();     // Artifact extends S3.File
}

public interface Artifact extends S3.File {
    // getValueAsStream(), getSize(), etc.
}
```

Usage:

```java
Repository repo = bucket.as(Repository.class);
repo.groups().forEach(group -> {
    group.artifacts().forEach(artifact -> {
        artifact.versions().forEach(version -> {
            System.out.println(version.file().getAbsoluteName());
        });
    });
});
```
