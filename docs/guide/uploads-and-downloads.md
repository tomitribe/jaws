# Uploads & Downloads

JAWS provides upload and download support through the AWS S3 Transfer Manager,
which handles multipart transfers and retries automatically.

## Uploading Files

### From S3.Dir

The `S3.Dir` interface includes an `upload()` method that uses the local file's
name as the S3 key:

```java
public interface Photos extends S3.Dir {
    // upload() is inherited from S3.Dir
}

Photos photos = bucket.as(Photos.class);
photos.upload(new File("/path/to/photo.jpg"));
```

### With progress tracking

Pass a `TransferListener` to monitor upload progress:

```java
photos.upload(new File("/path/to/large-video.mp4"), new TransferListener() {
    @Override
    public void transferInitiated(Context.TransferInitiated context) {
        System.out.println("Upload started");
    }

    @Override
    public void bytesTransferred(Context.BytesTransferred context) {
        System.out.println("Transferred: " + context.progressSnapshot().transferredBytes());
    }

    @Override
    public void transferComplete(Context.TransferComplete context) {
        System.out.println("Upload complete");
    }

    @Override
    public void transferFailed(Context.TransferFailed context) {
        System.err.println("Upload failed: " + context.exception().getMessage());
    }
});
```

### From S3File

You can also upload directly to a specific `S3File`:

```java
S3File target = bucket.root().file("path/to/destination.txt");
target.upload(new File("/path/to/source.txt"));
```

### From S3Bucket

The bucket provides low-level upload methods:

```java
// Upload a file
bucket.upload("path/to/key.txt", new File("/local/file.txt"));

// Upload from an input stream
bucket.upload("path/to/key.txt", inputStream, contentLength);

// Upload a string
bucket.putObject("path/to/key.txt", "file contents");
```

## Downloading Files

### From S3Bucket

```java
FileDownload download = bucket.download("path/to/key.txt",
    new File("/local/destination.txt"));

// Wait for completion
download.completionFuture().join();
```

## Writing Content

### Through S3.File proxy

The `S3.File` interface provides methods for writing content directly:

```java
public interface Config extends S3.File {}

Config config = root.config();
config.setValueAsString("{\"key\": \"value\"}");
config.setValueAsFile(new File("/path/to/config.json"));
config.setValueAsStream(inputStream);
```

### Through S3File

```java
S3File file = bucket.root().file("path/to/file.txt");
file.setValueAsString("content");
file.setValueAsFile(new File("/local/file.txt"));
file.setValueAsStream(inputStream);
```

## Reading Content

### Through S3.File proxy

```java
Config config = root.config();
String content = config.getValueAsString();
InputStream stream = config.getValueAsStream();
long size = config.getSize();
String etag = config.getETag();
Instant modified = config.getLastModified();
```

### Through S3File

```java
S3File file = bucket.root().file("config.json");
String content = file.getValueAsString();
InputStream stream = file.getValueAsStream();
```
