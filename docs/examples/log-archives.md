# Log Archives by Date

This example models date-partitioned application logs stored in S3.
It demonstrates method-level `@Filter` for selecting log types and
`@Parent` for navigating back up the hierarchy.

## S3 Bucket Structure

```text
logs/
  production/
    2024/
      12/
        30/
          app-2024-12-30.log.gz
          access-2024-12-30.log.gz
        31/
          app-2024-12-31.log.gz
          access-2024-12-31.log.gz
    2025/
      01/
        01/
          app-2025-01-01.log.gz
          access-2025-01-01.log.gz
        02/
          app-2025-01-02.log.gz
          access-2025-01-02.log.gz
      02/
        01/
          app-2025-02-01.log.gz
          access-2025-02-01.log.gz
  staging/
    2025/
      01/
        01/
          app-2025-01-01.log.gz
```

## Interfaces

```java
// Root of the logs bucket
public interface LogBucket extends S3.Dir {
    Stream<Environment> environments();
    Environment environment(String name);
}

// An environment (production, staging)
public interface Environment extends S3.Dir {
    Stream<YearDir> years();
    YearDir year(String year);

    // All year directories (immediate listing — S3.Dir type)
    Stream<YearDir> allYears();
}

// Year directory (2024, 2025, ...)
public interface YearDir extends S3.Dir {
    Stream<MonthDir> months();
    MonthDir month(String month);
}

// Month directory (01, 02, ..., 12)
public interface MonthDir extends S3.Dir {
    Stream<DayDir> days();
    DayDir day(String day);
}

// Day directory (01, 02, ..., 31)
public interface DayDir extends S3.Dir {
    Stream<LogFile> logs();

    @Filter(IsAppLog.class)
    Stream<LogFile> appLogs();

    @Filter(IsAccessLog.class)
    Stream<LogFile> accessLogs();

    // Navigate back to the environment (day → month → year → env)
    @Parent(3)
    Environment environment();
}

// A single log file
public interface LogFile extends S3.File {
}
```

### Filter predicates

```java
public class IsAppLog implements Predicate<S3File> {
    @Override
    public boolean test(S3File file) {
        return file.getName().startsWith("app-");
    }
}

public class IsAccessLog implements Predicate<S3File> {
    @Override
    public boolean test(S3File file) {
        return file.getName().startsWith("access-");
    }
}
```

## Usage

### Navigate the date hierarchy

Access logs by walking the year → month → day structure:

```java
LogBucket root = bucket.as(LogBucket.class);
Environment prod = root.environment("production");

DayDir day = prod.year("2025").month("01").day("01");
day.logs().forEach(log -> {
    System.out.println(log.file().getName());
});
// Output:
//   app-2025-01-01.log.gz
//   access-2025-01-01.log.gz
```

### Filter by log type

Method-level `@Filter` on `DayDir` separates application logs from access logs:

```java
DayDir day = prod.year("2025").month("01").day("01");

day.appLogs().forEach(log -> {
    System.out.println(log.file().getName());
});
// Output:
//   app-2025-01-01.log.gz

day.accessLogs().forEach(log -> {
    System.out.println(log.file().getName());
});
// Output:
//   access-2025-01-01.log.gz
```

### List year directories

Since `YearDir` extends `S3.Dir`, the listing returns only immediate
directories — no `@Recursive` annotation needed:

```java
prod.allYears().forEach(year -> {
    System.out.println(year.file().getName());
});
// Output:
//   2024
//   2025
```

### Navigate back with @Parent

From any day directory, `@Parent(3)` jumps back to the environment:

```java
DayDir day = prod.year("2025").month("01").day("01");

// @Parent(3): day → month → year → environment
Environment env = day.environment();
System.out.println(env.file().getName());
// Output: production
```

## Features Used

- [Immediate listing](../guide/walking-and-listing.md#immediate-listing-default) — `allYears()` returns immediate year directories
- [`@Filter` (method-level)](../guide/filtering.md#client-side-filtering-with-filter) — `appLogs()` and `accessLogs()` on `DayDir` filter by log type
- [`@Parent`](../api/annotations/parent.md) — `@Parent(3)` navigates from day back to environment

## Test

See [LogArchivesTest.java](https://github.com/tomitribe/jaws/blob/master/jaws-s3/src/test/java/org/tomitribe/jaws/s3/examples/LogArchivesTest.java) for a working test of this example.
