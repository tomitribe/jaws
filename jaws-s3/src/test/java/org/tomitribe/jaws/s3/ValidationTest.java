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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ValidationTest {

    @RegisterExtension
    public MockS3Extension mockS3 = new MockS3Extension();
    private S3Bucket bucket;

    @BeforeEach
    public void setUp() {
        final S3Client s3Client = new S3Client(mockS3.getS3Client());
        bucket = s3Client.createBucket("bucket");
        bucket.put("alice.json", "");
        bucket.put("bob.json", "");
        bucket.put("carol.xml", "");
        bucket.put("report-2024.csv", "");
        bucket.put("report-2024.csv.bak", "");
    }

    private S3File file(final String name) {
        return bucket.getFile(name);
    }

    // ---------------------------------------------------------------
    // @Match include on type
    // ---------------------------------------------------------------

    @Test
    public void matchIncludeOnType() {
        final Validation v = Validation.builder()
                .type(JsonFile.class)
                .build();

        assertTrue(v.test(file("alice.json")));
        assertFalse(v.test(file("carol.xml")));
    }

    // ---------------------------------------------------------------
    // @Match exclude on type
    // ---------------------------------------------------------------

    @Test
    public void matchExcludeOnType() {
        final Validation v = Validation.builder()
                .type(NotXml.class)
                .build();

        assertTrue(v.test(file("alice.json")));
        assertFalse(v.test(file("carol.xml")));
    }

    // ---------------------------------------------------------------
    // @Match include + exclude combined
    // ---------------------------------------------------------------

    @Test
    public void matchIncludeAndExclude() throws Exception {
        final Validation v = Validation.builder()
                .method(MatchMethods.class.getMethod("jsonExceptBob"))
                .build();

        assertTrue(v.test(file("alice.json")));
        assertFalse(v.test(file("bob.json")));
        assertFalse(v.test(file("carol.xml")));
    }

    // ---------------------------------------------------------------
    // @Suffix include on type
    // ---------------------------------------------------------------

    @Test
    public void suffixIncludeOnType() {
        final Validation v = Validation.builder()
                .type(CsvFile.class)
                .build();

        assertTrue(v.test(file("report-2024.csv")));
        assertFalse(v.test(file("alice.json")));
    }

    // ---------------------------------------------------------------
    // @Suffix exclude on type
    // ---------------------------------------------------------------

    @Test
    public void suffixExcludeOnType() {
        final Validation v = Validation.builder()
                .type(NotBak.class)
                .build();

        assertTrue(v.test(file("report-2024.csv")));
        assertFalse(v.test(file("report-2024.csv.bak")));
    }

    // ---------------------------------------------------------------
    // @Prefix on method with prefix(true) — default
    // ---------------------------------------------------------------

    @Test
    public void prefixOnMethodDefault() throws Exception {
        final Validation v = Validation.builder()
                .method(PrefixMethods.class.getMethod("reportFiles"))
                .build();

        assertTrue(v.test(file("report-2024.csv")));
        assertFalse(v.test(file("alice.json")));
    }

    // ---------------------------------------------------------------
    // @Prefix on method with prefix(false)
    // ---------------------------------------------------------------

    @Test
    public void prefixOnMethodSkipped() throws Exception {
        final Validation v = Validation.builder()
                .method(PrefixMethods.class.getMethod("reportFiles"))
                .prefix(false)
                .build();

        // With prefix(false), the @Prefix check is skipped so everything passes
        assertTrue(v.test(file("report-2024.csv")));
        assertTrue(v.test(file("alice.json")));
    }

    // ---------------------------------------------------------------
    // @Filter on type
    // ---------------------------------------------------------------

    @Test
    public void filterOnType() {
        final Validation v = Validation.builder()
                .type(FilteredFile.class)
                .build();

        assertTrue(v.test(file("alice.json")));
        assertFalse(v.test(file("carol.xml")));
    }

    // ---------------------------------------------------------------
    // Combined type + method annotations
    // ---------------------------------------------------------------

    @Test
    public void combinedTypeAndMethod() throws Exception {
        // Type has @Suffix(".json"), method has @Match("alice\\.json")
        final Validation v = Validation.builder()
                .type(JsonFile2.class)
                .method(CombinedMethods.class.getMethod("aliceOnly"))
                .build();

        assertTrue(v.test(file("alice.json")));
        assertFalse(v.test(file("bob.json")));
        assertFalse(v.test(file("carol.xml")));
    }

    // ---------------------------------------------------------------
    // No annotations — everything passes
    // ---------------------------------------------------------------

    @Test
    public void noAnnotations() {
        final Validation v = Validation.builder()
                .type(PlainInterface.class)
                .build();

        assertTrue(v.test(file("alice.json")));
        assertTrue(v.test(file("carol.xml")));
        assertTrue(v.test(file("report-2024.csv")));
    }

    @Test
    public void emptyBuilder() {
        final Validation v = Validation.builder().build();

        assertTrue(v.test(file("alice.json")));
        assertTrue(v.test(file("carol.xml")));
    }

    // ---------------------------------------------------------------
    // Test interfaces and methods
    // ---------------------------------------------------------------

    @Match(".*\\.json")
    public interface JsonFile extends S3 {
    }

    @Match(value = ".*\\.xml", exclude = true)
    public interface NotXml extends S3 {
    }

    @Suffix(".csv")
    public interface CsvFile extends S3 {
    }

    @Suffix(value = ".bak", exclude = true)
    public interface NotBak extends S3 {
    }

    @Filter(JsonOnlyFilter.class)
    public interface FilteredFile extends S3 {
    }

    @Suffix(".json")
    public interface JsonFile2 extends S3 {
    }

    public interface PlainInterface extends S3 {
    }

    public interface MatchMethods {
        @Match(".*\\.json")
        @Match(value = "bob\\.json", exclude = true)
        Stream<S3File> jsonExceptBob();
    }

    public interface PrefixMethods {
        @Prefix("report")
        Stream<S3File> reportFiles();
    }

    public interface CombinedMethods {
        @Match("alice\\.json")
        Stream<S3File> aliceOnly();
    }

    public static class JsonOnlyFilter implements Predicate<S3File> {
        @Override
        public boolean test(final S3File s3File) {
            return s3File.getName().endsWith(".json");
        }
    }
}
