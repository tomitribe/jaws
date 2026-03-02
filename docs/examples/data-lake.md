# Data Lake / ETL Pipeline

This example models a data lake with Hive-style partitioned directories
stored in S3. It demonstrates `@Name` for keys containing `=` characters,
`@Marker` for pagination control, `@Prefix` for partition searches,
and collection return types like `List<>` and arrays.

## S3 Bucket Structure

```text
data-lake/
  raw/
    events-2025-01-15.json
    events-2025-01-16.json
    events-2025-02-01.json
  processed/
    year=2025/
      month=01/
        day=15/
          part-00000.parquet
          part-00001.parquet
          _SUCCESS
        day=16/
          part-00000.parquet
          _SUCCESS
      month=02/
        day=01/
          part-00000.parquet
          _SUCCESS
  curated/
    daily-summary-2025-01-15.csv
    daily-summary-2025-01-16.csv
    daily-summary-2025-02-01.csv
    monthly-summary-2025-01.csv
    monthly-summary-2025-02.csv
```

## Interfaces

```java
// Root of the data lake
public interface DataLake extends S3.Dir {
    Raw raw();
    Processed processed();
    Curated curated();
}

// Raw ingestion zone — unpartitioned event files
public interface Raw extends S3.Dir {
    Stream<EventFile> events();

    // Start listing after the last January event
    @Marker("events-2025-01-16.json")
    Stream<EventFile> eventsSinceFeb();

    // Only events matching a prefix
    @Prefix("events-2025-01")
    Stream<EventFile> januaryEvents();
}

// Processed zone — Hive-style year=YYYY/month=MM/day=DD partitions
public interface Processed extends S3.Dir {

    // @Name maps to "year=2025" — the = character can't be a method name
    @Name("year=2025")
    YearPartition year2025();

    // Walk all parquet files across all partitions
    @Walk
    @Filter(IsParquet.class)
    Stream<S3File> allParquetFiles();

    // All year partitions as a list
    List<YearPartition> years();
}

// year=YYYY partition
public interface YearPartition extends S3.Dir {

    // Named lookup: pass "month=01" to get that partition
    MonthPartition partition(String name);

    @Name("month=01")
    MonthPartition month01();

    @Name("month=02")
    MonthPartition month02();

    // All month partitions as an array
    MonthPartition[] months();
}

// month=MM partition
public interface MonthPartition extends S3.Dir {

    DayPartition partition(String name);

    // All day partitions as a list
    List<DayPartition> days();
}

// day=DD partition — contains the actual data files
public interface DayPartition extends S3.Dir {

    // All data files in this partition (parquet + _SUCCESS)
    Stream<DataFile> dataFiles();

    // Only parquet files
    @Filter(IsParquet.class)
    Stream<DataFile> parquetFiles();
}

// A data file (parquet, _SUCCESS marker, etc.)
public interface DataFile extends S3.File {
}

// A raw event file
public interface EventFile extends S3.File {
}

// Curated zone — summary reports
public interface Curated extends S3.Dir {
    Stream<ReportFile> reports();

    @Prefix("daily-summary")
    Stream<ReportFile> dailySummaries();

    @Prefix("monthly-summary")
    Stream<ReportFile> monthlySummaries();

    // Start listing after a known key
    @Marker("daily-summary-2025-01-15.csv")
    Stream<ReportFile> reportsAfterJan15();
}

// A curated report file
public interface ReportFile extends S3.File {
}
```

### Filter predicate

```java
public class IsParquet implements Predicate<S3File> {
    @Override
    public boolean test(S3File file) {
        return file.getName().endsWith(".parquet");
    }
}
```

## Usage

### Navigate Hive-style partitions

The `@Name` annotation maps methods to key segments containing `=`:

```java
DataLake lake = bucket.as(DataLake.class);

YearPartition year = lake.processed().year2025();
MonthPartition jan = year.month01();

jan.days().forEach(day -> {
    System.out.println(day.file().getName());
});
// Output:
//   day=15
//   day=16
```

### Walk all parquet files

`@Walk` combined with `@Filter` finds every parquet file across all partitions:

```java
lake.processed().allParquetFiles().forEach(f -> {
    System.out.println(f.getAbsoluteName());
});
// Output:
//   data-lake/processed/year=2025/month=01/day=15/part-00000.parquet
//   data-lake/processed/year=2025/month=01/day=15/part-00001.parquet
//   data-lake/processed/year=2025/month=01/day=16/part-00000.parquet
//   data-lake/processed/year=2025/month=02/day=01/part-00000.parquet
```

### Prefix search for summaries

`@Prefix` narrows listings server-side:

```java
lake.curated().dailySummaries().forEach(r -> {
    System.out.println(r.file().getName());
});
// Output:
//   daily-summary-2025-01-15.csv
//   daily-summary-2025-01-16.csv
//   daily-summary-2025-02-01.csv

lake.curated().monthlySummaries().forEach(r -> {
    System.out.println(r.file().getName());
});
// Output:
//   monthly-summary-2025-01.csv
//   monthly-summary-2025-02.csv
```

### Pagination with @Marker

`@Marker` starts the listing after the given key, useful for resuming
interrupted scans over large datasets:

```java
// Skip everything up to and including daily-summary-2025-01-15.csv
lake.curated().reportsAfterJan15().forEach(r -> {
    System.out.println(r.file().getName());
});
// Output:
//   daily-summary-2025-01-16.csv
//   daily-summary-2025-02-01.csv
//   monthly-summary-2025-01.csv
//   monthly-summary-2025-02.csv
```

### List and array return types

Methods can return `List<>` or arrays instead of `Stream<>`:

```java
// List<YearPartition> — all year partitions collected into a List
List<YearPartition> years = lake.processed().years();
System.out.println(years.size());
// Output: 1

// MonthPartition[] — all month partitions as an array
MonthPartition[] months = lake.processed().year2025().months();
System.out.println(months.length);
// Output: 2
```

## Features Used

- [`@Name`](../api/annotations/name.md) — maps methods to Hive-style keys like `year=2025` and `month=01`
- [`@Prefix`](../api/annotations/prefix.md) — server-side filtering for `dailySummaries()` and `monthlySummaries()`
- [`@Marker`](../api/annotations/marker.md) — resume listing from a known position with `reportsAfterJan15()`
- [`@Filter` (method-level)](../guide/filtering.md#client-side-filtering-with-filter) — `IsParquet` predicate on `allParquetFiles()` and `parquetFiles()`
- [`@Walk`](../guide/walking-and-listing.md#recursive-walk) — recursive traversal across all Hive partitions
- [`List<>` and array returns](../guide/typed-proxies.md#collections--stream-list-set-array) — `years()` returns `List<YearPartition>`, `months()` returns `MonthPartition[]`

## Test

See [DataLakeTest.java](https://github.com/tomitribe/jaws/blob/master/jaws-s3/src/test/java/org/tomitribe/jaws/s3/examples/DataLakeTest.java) for a working test of this example.
