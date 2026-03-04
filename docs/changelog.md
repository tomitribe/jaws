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
