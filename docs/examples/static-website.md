# Static Website Assets

This example models versioned static website deployments stored in S3.
It demonstrates type-level `@Filter` to categorize assets, default methods
for domain logic, and `getObjectMetadata()` for inspecting content types.

## S3 Bucket Structure

```text
deployments/
  20250301-143022/
    index.html
    manifest.json
    css/
      main.css
      reset.css
    js/
      app.js
      vendor.js
    images/
      logo.png
      hero.jpg
  20250215-091500/
    index.html
    manifest.json
    css/
      main.css
    js/
      app.js
    images/
      logo.png
```

## Interfaces

```java
// Root of the deployments bucket
public interface Deployments extends S3.Dir {
    Stream<Deployment> deployments();
    Deployment deployment(String timestamp);
}

// A single timestamped deployment
public interface Deployment extends S3.Dir {

    @Name("index.html")
    HtmlFile indexHtml();

    @Name("manifest.json")
    Manifest manifest();

    // Navigate into asset subdirectories
    CssDir css();
    JsDir js();
    ImagesDir images();

    // Top-level files only (index.html, manifest.json)
    Stream<Asset> assets();

    // Recursively list the full directory tree
    @Recursive
    Stream<S3.Dir> layout();

    // Parse the deployment timestamp from the directory name
    default LocalDateTime getDeployedAt() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
        return LocalDateTime.parse(file().getName(), fmt);
    }
}

// The css/ directory
public interface CssDir extends S3.Dir {
    Stream<CssFile> stylesheets();
}

// The js/ directory
public interface JsDir extends S3.Dir {
    Stream<JsFile> scripts();
}

// The images/ directory
public interface ImagesDir extends S3.Dir {
    Stream<ImageFile> images();
}

// manifest.json with a convenience method
public interface Manifest extends S3.File {

    default String getVersion() {
        // Parse the version from the JSON content
        String json = getValueAsString();
        int start = json.indexOf("\"version\"");
        int colon = json.indexOf(':', start);
        int open = json.indexOf('"', colon + 1);
        int close = json.indexOf('"', open + 1);
        return json.substring(open + 1, close);
    }
}

// Base type for all static assets
public interface Asset extends S3.File {

    default String getContentType() {
        return getObjectMetadata().contentType();
    }
}

// CSS files — type-level filter ensures only .css files are returned
@Filter(IsCss.class)
public interface CssFile extends S3.File {
}

// JS files — type-level filter ensures only .js files are returned
@Filter(IsJs.class)
public interface JsFile extends S3.File {
}

// Image files
@Filter(IsImage.class)
public interface ImageFile extends S3.File {
}

// HTML files
public interface HtmlFile extends S3.File {
}
```

### Filter predicates

```java
public class IsCss implements Predicate<S3File> {
    @Override
    public boolean test(S3File file) {
        return file.getName().endsWith(".css");
    }
}

public class IsJs implements Predicate<S3File> {
    @Override
    public boolean test(S3File file) {
        return file.getName().endsWith(".js");
    }
}

public class IsImage implements Predicate<S3File> {
    @Override
    public boolean test(S3File file) {
        String name = file.getName();
        return name.endsWith(".png")
            || name.endsWith(".jpg")
            || name.endsWith(".gif")
            || name.endsWith(".svg");
    }
}
```

## Usage

### List all deployments

The `getDeployedAt()` default method uses `file().getName()` to read the
directory name and parse it into a `LocalDateTime`:

```java
Deployments root = bucket.as(Deployments.class);

root.deployments().forEach(d -> {
    System.out.println(d.file().getName() + " → " + d.getDeployedAt());
});
// Output:
//   20250215-091500 → 2025-02-15T09:15
//   20250301-143022 → 2025-03-01T14:30:22
```

### Read the manifest and check the version

```java
Deployment latest = root.deployment("20250301-143022");

Manifest manifest = latest.manifest();
System.out.println(manifest.getVersion());
// Output: 1.4.2
```

### Filter assets by type

Each asset subdirectory is modeled as an `S3.Dir`, so the listing is scoped
to the correct prefix. Type-level `@Filter` annotations on `CssFile`, `JsFile`,
and `ImageFile` add an extra layer of safety:

```java
Deployment deploy = root.deployment("20250301-143022");

deploy.css().stylesheets().forEach(css -> {
    System.out.println(css.file().getName() + " — " + css.getSize() + " bytes");
});
// Output:
//   main.css — 14230 bytes
//   reset.css — 1082 bytes

deploy.js().scripts().forEach(js -> {
    System.out.println(js.file().getName());
});
// Output:
//   app.js
//   vendor.js
```

### Inspect top-level assets

Without `@Recursive`, `Stream<Asset>` performs a single delimiter-based listing
and returns only the files at the deployment level:

```java
Deployment deploy = root.deployment("20250301-143022");

deploy.assets().forEach(asset -> {
    System.out.println(asset.file().getName() + " → " + asset.getContentType());
});
// Output:
//   index.html → text/html
//   manifest.json → application/json
```

### View the deployment layout

`@Recursive` recursively descends into every subdirectory. Because the element
type is `S3.Dir`, only directories are returned — files are filtered out:

```java
Deployment deploy = root.deployment("20250301-143022");

deploy.layout().forEach(entry -> {
    System.out.println(entry.file().getAbsoluteName());
});
// Output:
//   deployments/20250301-143022/css
//   deployments/20250301-143022/images
//   deployments/20250301-143022/js
```

## Features Used

- [`S3.Dir`](../guide/directories-and-files.md#s3dir--directories) — `CssDir`, `JsDir`, and `ImagesDir` scope listings to the correct subdirectory
- [`@Filter` (type-level)](../guide/filtering.md#type-level-filters) — `CssFile`, `JsFile`, and `ImageFile` interfaces carry their own filter predicates
- [`@Recursive`](../guide/walking-and-listing.md#recursive-listing) — `layout()` recursively lists the deployment tree
- [`S3.File` content access](../guide/directories-and-files.md#s3file--files-with-content) — `getValueAsString()` on `Manifest`, `getSize()` on assets
- [Default methods](../guide/typed-proxies.md#default-methods) — `Deployment.getDeployedAt()` parses the directory name via `file().getName()`, `Manifest.getVersion()` and `Asset.getContentType()` add domain logic
- [`getObjectMetadata()`](../api/s3-interfaces.md#s3file) — `Asset.getContentType()` reads the S3 content type header
- [`@Name`](../api/annotations/name.md) — maps methods to `index.html` and `manifest.json`

## Test

See [StaticWebsiteTest.java](https://github.com/tomitribe/jaws/blob/master/jaws-s3/src/test/java/org/tomitribe/jaws/s3/examples/StaticWebsiteTest.java) for a working test of this example.
