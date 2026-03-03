/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.tomitribe.jaws.s3.examples;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.tomitribe.jaws.s3.Filter;
import org.tomitribe.jaws.s3.Marker;
import org.tomitribe.jaws.s3.MockS3Rule;
import org.tomitribe.jaws.s3.Name;
import org.tomitribe.jaws.s3.Prefix;
import org.tomitribe.jaws.s3.S3;
import org.tomitribe.jaws.s3.S3Client;
import org.tomitribe.jaws.s3.S3File;
import org.tomitribe.jaws.s3.Walk;
import org.tomitribe.util.Join;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

/**
 * Tests modeled after the Data Lake example in docs/examples/data-lake.md.
 *
 * Verifies @Name for Hive-style keys, @Walk + @Filter, @Prefix, @Marker,
 * and List<>/array return types.
 */
public class DataLakeTest {

    @Rule
    public MockS3Rule mockS3 = new MockS3Rule();
    private S3Client s3Client;

    @Before
    public final void setUp() throws Exception {
        this.s3Client = new S3Client(mockS3.getS3Client());

        s3Client.createBucket("data-lake")
                .put("raw/events-2025-01-15.json", "{}")
                .put("raw/events-2025-01-16.json", "{}")
                .put("raw/events-2025-02-01.json", "{}")
                .put("processed/year=2025/month=01/day=15/part-00000.parquet", "")
                .put("processed/year=2025/month=01/day=15/part-00001.parquet", "")
                .put("processed/year=2025/month=01/day=15/_SUCCESS", "")
                .put("processed/year=2025/month=01/day=16/part-00000.parquet", "")
                .put("processed/year=2025/month=01/day=16/_SUCCESS", "")
                .put("processed/year=2025/month=02/day=01/part-00000.parquet", "")
                .put("processed/year=2025/month=02/day=01/_SUCCESS", "")
                .put("curated/daily-summary-2025-01-15.csv", "")
                .put("curated/daily-summary-2025-01-16.csv", "")
                .put("curated/daily-summary-2025-02-01.csv", "")
                .put("curated/monthly-summary-2025-01.csv", "")
                .put("curated/monthly-summary-2025-02.csv", "");
    }

    private DataLake lake() {
        return s3Client.getBucket("data-lake").as(DataLake.class);
    }

    /**
     * @Name("year=2025") should navigate to the Hive-style partition.
     */
    @Test
    public void navigateHivePartitions() throws Exception {
        final YearPartition year = lake().processed().year2025();
        Assert.assertEquals("year=2025", year.file().getName());

        final MonthPartition jan = year.month01();
        Assert.assertEquals("month=01", jan.file().getName());
    }

    /**
     * Named lookup with partition(String) should resolve Hive keys.
     */
    @Test
    public void partitionNamedLookup() throws Exception {
        final MonthPartition feb = lake().processed().year2025().partition("month=02");
        Assert.assertEquals("month=02", feb.file().getName());
    }

    /**
     * List<DayPartition> days() should return day partitions as a List.
     */
    @Test
    public void listDayPartitions() throws Exception {
        final List<DayPartition> days = lake().processed().year2025().month01().days();

        final List<String> names = days.stream()
                .map(d -> d.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("day=15\nday=16", Join.join("\n", names));
    }

    /**
     * List<YearPartition> years() should return year partitions as a List.
     */
    @Test
    public void listYearPartitions() throws Exception {
        final List<YearPartition> years = lake().processed().years();
        assertEquals(1, years.size());
        Assert.assertEquals("year=2025", years.get(0).file().getName());
    }

    /**
     * MonthPartition[] months() should return month partitions as an array.
     */
    @Test
    public void arrayMonthPartitions() throws Exception {
        final MonthPartition[] months = lake().processed().year2025().months();
        assertEquals(2, months.length);
    }

    /**
     * Stream<DataFile> dataFiles() should return all files in a day partition.
     */
    @Test
    public void dayPartitionDataFiles() throws Exception {
        final DayPartition day15 = lake().processed().year2025().month01().partition("day=15");

        final List<String> names = day15.dataFiles()
                .map(f -> f.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("_SUCCESS\npart-00000.parquet\npart-00001.parquet", Join.join("\n", names));
    }

    /**
     * @Filter(IsParquet) on DayPartition should return only parquet files.
     */
    @Test
    public void dayPartitionParquetFiles() throws Exception {
        final DayPartition day15 = lake().processed().year2025().month01().partition("day=15");

        final List<String> names = day15.parquetFiles()
                .map(f -> f.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("part-00000.parquet\npart-00001.parquet", Join.join("\n", names));
    }

    /**
     * @Walk @Filter(IsParquet) Stream<S3File> should walk all partitions
     * and return only parquet files.
     */
    @Test
    public void walkAllParquetFiles() throws Exception {
        final List<String> paths = lake().processed().allParquetFiles()
                .map(S3File::getAbsoluteName)
                .sorted()
                .collect(Collectors.toList());

        assertEquals("" +
                "processed/year=2025/month=01/day=15/part-00000.parquet\n" +
                "processed/year=2025/month=01/day=15/part-00001.parquet\n" +
                "processed/year=2025/month=01/day=16/part-00000.parquet\n" +
                "processed/year=2025/month=02/day=01/part-00000.parquet", Join.join("\n", paths));
    }

    /**
     * @Prefix("daily-summary") Stream<ReportFile> should return only daily summaries.
     */
    @Test
    public void dailySummariesPrefix() throws Exception {
        final List<String> names = lake().curated().dailySummaries()
                .map(r -> r.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("" +
                "daily-summary-2025-01-15.csv\n" +
                "daily-summary-2025-01-16.csv\n" +
                "daily-summary-2025-02-01.csv", Join.join("\n", names));
    }

    /**
     * @Prefix("monthly-summary") Stream<ReportFile> should return only monthly summaries.
     */
    @Test
    public void monthlySummariesPrefix() throws Exception {
        final List<String> names = lake().curated().monthlySummaries()
                .map(r -> r.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("monthly-summary-2025-01.csv\nmonthly-summary-2025-02.csv", Join.join("\n", names));
    }

    /**
     * @Marker should start listing after the given key.
     */
    @Test
    public void markerReportsAfterJan15() throws Exception {
        final List<String> names = lake().curated().reportsAfterJan15()
                .map(r -> r.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("" +
                "daily-summary-2025-01-16.csv\n" +
                "daily-summary-2025-02-01.csv\n" +
                "monthly-summary-2025-01.csv\n" +
                "monthly-summary-2025-02.csv", Join.join("\n", names));
    }

    /**
     * Stream<EventFile> events() returns all raw event files.
     */
    @Test
    public void rawEvents() throws Exception {
        final List<String> names = lake().raw().events()
                .map(e -> e.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("" +
                "events-2025-01-15.json\n" +
                "events-2025-01-16.json\n" +
                "events-2025-02-01.json", Join.join("\n", names));
    }

    /**
     * @Prefix("events-2025-01") Stream<EventFile> should return only January events.
     */
    @Test
    public void januaryEventsPrefix() throws Exception {
        final List<String> names = lake().raw().januaryEvents()
                .map(e -> e.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("events-2025-01-15.json\nevents-2025-01-16.json", Join.join("\n", names));
    }

    /**
     * @Marker on raw events should start listing after the marker key.
     */
    @Test
    public void rawEventsSinceFeb() throws Exception {
        final List<String> names = lake().raw().eventsSinceFeb()
                .map(e -> e.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("events-2025-02-01.json", Join.join("\n", names));
    }

    // ---------------------------------------------------------------
    // Test interfaces — modeled after docs/examples/data-lake.md
    // ---------------------------------------------------------------

    public interface DataLake extends S3.Dir {
        Raw raw();
        Processed processed();
        Curated curated();
    }

    public interface Raw extends S3.Dir {
        Stream<EventFile> events();

        @Marker("events-2025-01-16.json")
        Stream<EventFile> eventsSinceFeb();

        @Prefix("events-2025-01")
        Stream<EventFile> januaryEvents();
    }

    public interface Processed extends S3.Dir {
        @Name("year=2025")
        YearPartition year2025();

        @Walk
        @Filter(IsParquet.class)
        Stream<S3File> allParquetFiles();

        List<YearPartition> years();
    }

    public interface YearPartition extends S3.Dir {
        MonthPartition partition(String name);

        @Name("month=01")
        MonthPartition month01();

        @Name("month=02")
        MonthPartition month02();

        MonthPartition[] months();
    }

    public interface MonthPartition extends S3.Dir {
        DayPartition partition(String name);
        List<DayPartition> days();
    }

    public interface DayPartition extends S3.Dir {
        Stream<DataFile> dataFiles();

        @Filter(IsParquet.class)
        Stream<DataFile> parquetFiles();
    }

    public interface DataFile extends S3.File {
    }

    public interface EventFile extends S3.File {
    }

    public interface Curated extends S3.Dir {
        Stream<ReportFile> reports();

        @Prefix("daily-summary")
        Stream<ReportFile> dailySummaries();

        @Prefix("monthly-summary")
        Stream<ReportFile> monthlySummaries();

        @Marker("daily-summary-2025-01-15.csv")
        Stream<ReportFile> reportsAfterJan15();
    }

    public interface ReportFile extends S3.File {
    }

    // ---------------------------------------------------------------
    // Filter predicate
    // ---------------------------------------------------------------

    public static class IsParquet implements Predicate<S3File> {
        @Override
        public boolean test(final S3File file) {
            return file.getName().endsWith(".parquet");
        }
    }
}
