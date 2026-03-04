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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.tomitribe.jaws.s3.Filter;
import org.tomitribe.jaws.s3.MockS3Extension;
import org.tomitribe.jaws.s3.Parent;
import org.tomitribe.jaws.s3.S3;
import org.tomitribe.jaws.s3.S3Client;
import org.tomitribe.jaws.s3.S3File;
import org.tomitribe.util.Join;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests modeled after the Log Archives example in docs/examples/log-archives.md.
 *
 * Verifies immediate listing for S3.Dir types, @Filter, and @Parent.
 */
public class LogArchivesTest {

    @RegisterExtension
    public MockS3Extension mockS3 = new MockS3Extension();
    private S3Client s3Client;

    @BeforeEach
    public final void setUp() throws Exception {
        this.s3Client = new S3Client(mockS3.getS3Client());

        s3Client.createBucket("logs")
                .put("production/2024/12/30/app-2024-12-30.log.gz", "")
                .put("production/2024/12/30/access-2024-12-30.log.gz", "")
                .put("production/2024/12/31/app-2024-12-31.log.gz", "")
                .put("production/2024/12/31/access-2024-12-31.log.gz", "")
                .put("production/2025/01/01/app-2025-01-01.log.gz", "")
                .put("production/2025/01/01/access-2025-01-01.log.gz", "")
                .put("production/2025/01/02/app-2025-01-02.log.gz", "")
                .put("production/2025/01/02/access-2025-01-02.log.gz", "")
                .put("production/2025/02/01/app-2025-02-01.log.gz", "")
                .put("production/2025/02/01/access-2025-02-01.log.gz", "")
                .put("staging/2025/01/01/app-2025-01-01.log.gz", "");
    }

    private Environment prod() {
        return s3Client.getBucket("logs").as(LogBucket.class).environment("production");
    }

    /**
     * Stream<YearDir> should return only immediate year directories.
     */
    @Test
    public void listYearsImmediate() throws Exception {
        final List<String> names = prod().allYears()
                .map(y -> y.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("2024\n2025", Join.join("\n", names));
    }

    /**
     * Stream<LogFile> on DayDir returns files in that day.
     */
    @Test
    public void dayLogs() throws Exception {
        final DayDir day = prod().year("2025").month("01").day("01");

        final List<String> names = day.logs()
                .map(log -> log.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("access-2025-01-01.log.gz\napp-2025-01-01.log.gz", Join.join("\n", names));
    }

    /**
     * @Filter(IsAppLog) on DayDir returns only app logs in that day.
     */
    @Test
    public void dayAppLogs() throws Exception {
        final DayDir day = prod().year("2025").month("01").day("01");

        final List<String> names = day.appLogs()
                .map(log -> log.file().getName())
                .collect(Collectors.toList());

        assertEquals("app-2025-01-01.log.gz", Join.join("\n", names));
    }

    /**
     * @Parent(3) from day should navigate back to the environment.
     */
    @Test
    public void parentFromDay() throws Exception {
        final DayDir day = prod().year("2025").month("01").day("01");

        final Environment env = day.environment();
        assertEquals("production", env.file().getName());
    }

    /**
     * Stream<Environment> on LogBucket returns environment directories.
     */
    @Test
    public void listEnvironments() throws Exception {
        final LogBucket root = s3Client.getBucket("logs").as(LogBucket.class);

        final List<String> names = root.environments()
                .map(e -> e.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("production\nstaging", Join.join("\n", names));
    }

    // ---------------------------------------------------------------
    // Test interfaces — modeled after docs/examples/log-archives.md
    // ---------------------------------------------------------------

    public interface LogBucket extends S3.Dir {
        Stream<Environment> environments();
        Environment environment(String name);
    }

    public interface Environment extends S3.Dir {
        Stream<YearDir> years();
        YearDir year(String year);

        Stream<YearDir> allYears();
    }

    public interface YearDir extends S3.Dir {
        Stream<MonthDir> months();
        MonthDir month(String month);
    }

    public interface MonthDir extends S3.Dir {
        Stream<DayDir> days();
        DayDir day(String day);
    }

    public interface DayDir extends S3.Dir {
        Stream<LogFile> logs();

        @Filter(IsAppLog.class)
        Stream<LogFile> appLogs();

        @Filter(IsAccessLog.class)
        Stream<LogFile> accessLogs();

        @Parent(3)
        Environment environment();
    }

    public interface LogFile extends S3.File {
    }

    // ---------------------------------------------------------------
    // Filter predicates
    // ---------------------------------------------------------------

    public static class IsAppLog implements Predicate<S3File> {
        @Override
        public boolean test(final S3File file) {
            return file.getName().startsWith("app-");
        }
    }

    public static class IsAccessLog implements Predicate<S3File> {
        @Override
        public boolean test(final S3File file) {
            return file.getName().startsWith("access-");
        }
    }
}
