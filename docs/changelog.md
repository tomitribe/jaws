# Changelog

## 2.1.0

**Breaking changes**

- **All listings are immediate by default.** `Stream<T>`, `List<T>`, `Set<T>`, `Collection<T>`, and `T[]` methods now return only immediate children (one level) regardless of element type. Previously, plain `T` (not `S3.Dir` or `S3.File`) silently used a recursive flat listing.

- **`@Walk` replaced by `@Recursive`.** The `@Walk` annotation and its `maxDepth`/`minDepth` attributes have been removed. Use `@Recursive` to opt into recursive listing. There is no depth control; for depth-limited traversal, use `S3File.walk()` directly.

**New features**

- **Input validation on single-arg methods.** `@Prefix`, `@Suffix`, `@Match`, and `@Filter` annotations on the return type or method now validate the input name when calling single-arg proxy methods like `UserFile user(String name)`. An `IllegalArgumentException` is thrown if the name doesn't pass. This catches invalid keys at the call site rather than silently creating a proxy for a non-matching key.

**Migration**

| Before (2.0.x) | After (2.1.0) |
|---|---|
| `@Walk Stream<S3File> all()` | `@Recursive Stream<S3File> all()` |
| `@Walk Stream<S3.Dir> dirs()` | `@Recursive Stream<S3.Dir> dirs()` |
| `@Walk(maxDepth=1) Stream<X> items()` | `Stream<X> items()` (immediate is now the default) |
| `@Walk(minDepth=n, maxDepth=n) Stream<X> exact()` | Remove — use `S3File.walk()` manually |
| `Stream<T> items()` (plain T, was recursive) | Add `@Recursive` to preserve old behavior, or leave as-is for immediate |

## 2.0

**Breaking changes**

- **Upgraded to AWS SDK v2.** The library now uses the AWS SDK v2 (`S3AsyncClient`) exclusively. The v1 `S3Client` dependency has been removed.

- **`S3.of()` removed.** Use `s3Bucket.as(MyInterface.class)` and `s3File.as(MyInterface.class)` instead.

- **`S3.get()` renamed to `S3.file()`.** All call sites referencing `S3.get()` must be updated.

**New features**

- **`S3.Dir` and `S3.File` parent interfaces.** Typed proxies can now use `S3.Dir` and `S3.File` as return types to distinguish directories from files in listings. `Stream<S3.Dir>` returns only directories; `Stream<S3.File>` returns only files.

- **`@Prefix` support in `@Walk` and list operations.** The `@Prefix` annotation now works with `@Walk` and standard list methods, allowing prefix-scoped recursive and flat listings.

- **`@Delimiter` works with `@Walk`.** The `@Delimiter` annotation can now be combined with `@Walk` for more precise listing control.

- **`@Filter` on interfaces.** The `@Filter` annotation can now be placed on the interface type itself, applying the filter to all listing methods in that interface.

- **`@Suffix` and `@Match` annotations.** New annotations for filtering S3 listings by suffix or regex pattern. Both support an `exclude` flag and are repeatable.

- **`@Match` and `@Suffix` `exclude` flag.** Both annotations accept `exclude = true` to invert the match, filtering out entries that match rather than keeping them.

- **Expanded `upload()` methods.** Additional overloads for uploading content from `String`, `byte[]`, `File`, `Path`, and `InputStream`.

- **`ObjectMetadata` improvements.** Added `ObjectMetadata.from(File)` and `ObjectMetadata.from(Path)` for automatic content-type detection, plus a builder API.

- **Fluent `put()` methods on `S3Bucket`.** New convenience methods for writing content directly to a bucket path.

- **Default methods on `S3.File`.** Typed proxy interfaces extending `S3.File` now inherit default method implementations for common operations.

- **`jaws-s3-test` module.** New test module with `MockS3` for local S3 simulation and `S3Asserts` for fluent JUnit 5 assertions on bucket contents.

- **Annotations migrated into JAWS.** `@Name`, `@Parent`, `@Walk`, and other annotations previously in external libraries are now part of the JAWS project directly.
