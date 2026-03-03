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
package org.tomitribe.jaws.s3;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.tomitribe.util.Join;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

/**
 * Tests @Prefix with all Stream element-type scenarios:
 *
 *   - Stream<S3File>               (flat listing, no dir/file filtering)
 *   - Stream<X extends S3.File>    (files only)
 *   - Stream<X extends S3.Dir>     (directories only)
 *
 * Also verifies that methods WITHOUT @Prefix still work for each type.
 */
public class S3PrefixTest {

    @Rule
    public MockS3Rule mockS3 = new MockS3Rule();
    private S3Client s3Client;

    @Before
    public final void setUp() throws Exception {
        this.s3Client = new S3Client(mockS3.getS3Client());

        s3Client.createBucket("bucket")
                .put("reports/daily-2025-01-15.csv", "")
                .put("reports/daily-2025-01-16.csv", "")
                .put("reports/daily-2025-02-01.csv", "")
                .put("reports/monthly-2025-01.csv", "")
                .put("reports/monthly-2025-02.csv", "")
                .put("data/alpha/file1.txt", "")
                .put("data/alpha/file2.txt", "")
                .put("data/beta/file3.txt", "")
                .put("data/beta-extra/file4.txt", "")
                .put("data/gamma/file5.txt", "");
    }

    private Root root() {
        return s3Client.getBucket("bucket").as(Root.class);
    }

    // ---------------------------------------------------------------
    // Stream<S3File> — no dir/file filtering
    // ---------------------------------------------------------------

    @Test
    public void prefixStreamS3File() throws Exception {
        final List<String> names = root().reports().dailyAll()
                .map(S3File::getName)
                .sorted()
                .collect(Collectors.toList());

        assertEquals("daily-2025-01-15.csv\n" +
                "daily-2025-01-16.csv\n" +
                "daily-2025-02-01.csv", Join.join("\n", names));
    }

    @Test
    public void prefixStreamS3FileMonthly() throws Exception {
        final List<String> names = root().reports().monthlyAll()
                .map(S3File::getName)
                .sorted()
                .collect(Collectors.toList());

        assertEquals("monthly-2025-01.csv\n" +
                "monthly-2025-02.csv", Join.join("\n", names));
    }

    @Test
    public void noPrefixStreamS3File() throws Exception {
        final List<String> names = root().reports().all()
                .map(S3File::getName)
                .sorted()
                .collect(Collectors.toList());

        assertEquals("daily-2025-01-15.csv\n" +
                "daily-2025-01-16.csv\n" +
                "daily-2025-02-01.csv\n" +
                "monthly-2025-01.csv\n" +
                "monthly-2025-02.csv", Join.join("\n", names));
    }

    // ---------------------------------------------------------------
    // Stream<X extends S3.File> — files only
    // ---------------------------------------------------------------

    @Test
    public void prefixStreamFile() throws Exception {
        final List<String> names = root().reports().dailyReports()
                .map(r -> r.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("daily-2025-01-15.csv\n" +
                "daily-2025-01-16.csv\n" +
                "daily-2025-02-01.csv", Join.join("\n", names));
    }

    @Test
    public void prefixStreamFileMonthly() throws Exception {
        final List<String> names = root().reports().monthlyReports()
                .map(r -> r.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("monthly-2025-01.csv\n" +
                "monthly-2025-02.csv", Join.join("\n", names));
    }

    @Test
    public void noPrefixStreamFile() throws Exception {
        final List<String> names = root().reports().reports()
                .map(r -> r.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("daily-2025-01-15.csv\n" +
                "daily-2025-01-16.csv\n" +
                "daily-2025-02-01.csv\n" +
                "monthly-2025-01.csv\n" +
                "monthly-2025-02.csv", Join.join("\n", names));
    }

    // ---------------------------------------------------------------
    // Stream<X extends S3.Dir> — directories only
    // ---------------------------------------------------------------

    @Test
    public void prefixStreamDir() throws Exception {
        final List<String> names = root().data().betaDirs()
                .map(d -> d.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("beta\nbeta-extra", Join.join("\n", names));
    }

    @Test
    public void prefixStreamDirAlpha() throws Exception {
        final List<String> names = root().data().alphaDirs()
                .map(d -> d.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("alpha", Join.join("\n", names));
    }

    @Test
    public void noPrefixStreamDir() throws Exception {
        final List<String> names = root().data().groups()
                .map(d -> d.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("alpha\nbeta\nbeta-extra\ngamma", Join.join("\n", names));
    }

    // ---------------------------------------------------------------
    // Test interfaces
    // ---------------------------------------------------------------

    public interface Root extends S3.Dir {
        Reports reports();
        Data data();
    }

    public interface Reports extends S3.Dir {
        // Stream<S3File>
        Stream<S3File> all();

        @Prefix("daily")
        Stream<S3File> dailyAll();

        @Prefix("monthly")
        Stream<S3File> monthlyAll();

        // Stream<X extends S3.File>
        Stream<ReportFile> reports();

        @Prefix("daily")
        Stream<ReportFile> dailyReports();

        @Prefix("monthly")
        Stream<ReportFile> monthlyReports();
    }

    public interface Data extends S3.Dir {
        // Stream<X extends S3.Dir>
        Stream<GroupDir> groups();

        @Prefix("beta")
        Stream<GroupDir> betaDirs();

        @Prefix("alpha")
        Stream<GroupDir> alphaDirs();
    }

    public interface ReportFile extends S3.File {
    }

    public interface GroupDir extends S3.Dir {
        Stream<DataFile> dataFiles();
    }

    public interface DataFile extends S3.File {
    }
}
