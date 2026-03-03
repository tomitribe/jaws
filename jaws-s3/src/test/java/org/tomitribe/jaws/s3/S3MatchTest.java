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
import org.tomitribe.util.Join;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class S3MatchTest {

    @RegisterExtension
    public MockS3Extension mockS3 = new MockS3Extension();
    private S3Client s3Client;

    @BeforeEach
    public final void setUp() throws Exception {
        this.s3Client = new S3Client(mockS3.getS3Client());

        s3Client.createBucket("bucket")
                .put("assets/main.css", "")
                .put("assets/reset.css", "")
                .put("assets/app.js", "")
                .put("assets/vendor.js", "")
                .put("assets/logo.png", "")
                .put("assets/hero.jpg", "")
                .put("assets/index.html", "")
                .put("groups/team-alpha/file1.txt", "")
                .put("groups/team-beta/file2.txt", "")
                .put("groups/dept-alpha/file3.txt", "")
                .put("groups/dept-beta/file4.txt", "")
                .put("groups/org-gamma/file5.txt", "");
    }

    private Root root() {
        return s3Client.getBucket("bucket").as(Root.class);
    }

    // ---------------------------------------------------------------
    // All 10 return types with @Match
    // ---------------------------------------------------------------

    @Test
    public void returnArrayOfInterface() throws Exception {
        final MatchReturns returns = root().assets().file().as(MatchReturns.class);
        final Asset[] data = returns.returnArrayOfInterface();
        assertEquals("assets/main.css\nassets/reset.css", Join.join("\n", (Object[]) data));
    }

    @Test
    public void returnArrayOfS3File() throws Exception {
        final MatchReturns returns = root().assets().file().as(MatchReturns.class);
        final S3File[] data = returns.returnArrayOfS3File();
        assertEquals("main.css\nreset.css", Join.join("\n", S3File::getName, data));
    }

    @Test
    public void returnStreamOfInterface() throws Exception {
        final MatchReturns returns = root().assets().file().as(MatchReturns.class);
        final List<Asset> data = returns.returnStreamOfInterface().collect(Collectors.toList());
        assertEquals("assets/main.css\nassets/reset.css", Join.join("\n", data));
    }

    @Test
    public void returnStreamOfS3File() throws Exception {
        final MatchReturns returns = root().assets().file().as(MatchReturns.class);
        final List<S3File> data = returns.returnStreamOfS3File().collect(Collectors.toList());
        assertEquals("main.css\nreset.css", Join.join("\n", S3File::getName, data));
    }

    @Test
    public void returnListOfInterface() throws Exception {
        final MatchReturns returns = root().assets().file().as(MatchReturns.class);
        final List<Asset> data = returns.returnListOfInterface();
        assertEquals("assets/main.css\nassets/reset.css", Join.join("\n", data));
    }

    @Test
    public void returnListOfS3File() throws Exception {
        final MatchReturns returns = root().assets().file().as(MatchReturns.class);
        final List<S3File> data = returns.returnListOfS3File();
        assertEquals("main.css\nreset.css", Join.join("\n", S3File::getName, data));
    }

    @Test
    public void returnSetOfInterface() throws Exception {
        final MatchReturns returns = root().assets().file().as(MatchReturns.class);
        final List<String> data = returns.returnSetOfInterface().stream()
                .map(Asset::toString).sorted().collect(Collectors.toList());
        assertEquals("assets/main.css\nassets/reset.css", Join.join("\n", data));
    }

    @Test
    public void returnSetOfS3File() throws Exception {
        final MatchReturns returns = root().assets().file().as(MatchReturns.class);
        final List<String> data = returns.returnSetOfS3File().stream()
                .map(S3File::getName).sorted().collect(Collectors.toList());
        assertEquals("main.css\nreset.css", Join.join("\n", data));
    }

    @Test
    public void returnCollectionOfInterface() throws Exception {
        final MatchReturns returns = root().assets().file().as(MatchReturns.class);
        final Collection<Asset> data = returns.returnCollectionOfInterface();
        assertEquals("assets/main.css\nassets/reset.css", Join.join("\n", data));
    }

    @Test
    public void returnCollectionOfS3File() throws Exception {
        final MatchReturns returns = root().assets().file().as(MatchReturns.class);
        final Collection<S3File> data = returns.returnCollectionOfS3File();
        assertEquals("main.css\nreset.css", Join.join("\n", S3File::getName, data));
    }

    // ---------------------------------------------------------------
    // S3.File element type (direct and subtype)
    // ---------------------------------------------------------------

    @Test
    public void cssS3Files() throws Exception {
        final FileReturns returns = root().assets().file().as(FileReturns.class);
        final List<String> names = returns.cssS3Files()
                .map(f -> f.file().getName())
                .collect(Collectors.toList());
        assertEquals("main.css\nreset.css", Join.join("\n", names));
    }

    @Test
    public void cssAssetFiles() throws Exception {
        final FileReturns returns = root().assets().file().as(FileReturns.class);
        final List<String> names = returns.cssAssetFiles()
                .map(f -> f.file().getName())
                .collect(Collectors.toList());
        assertEquals("main.css\nreset.css", Join.join("\n", names));
    }

    // ---------------------------------------------------------------
    // S3.Dir element type (direct and subtype)
    // ---------------------------------------------------------------

    @Test
    public void alphaS3Dirs() throws Exception {
        final DirReturns returns = root().groups().file().as(DirReturns.class);
        final List<String> names = returns.alphaS3Dirs()
                .map(d -> d.file().getName())
                .sorted()
                .collect(Collectors.toList());
        assertEquals("dept-alpha\nteam-alpha", Join.join("\n", names));
    }

    @Test
    public void alphaGroupDirs() throws Exception {
        final DirReturns returns = root().groups().file().as(DirReturns.class);
        final List<String> names = returns.alphaGroupDirs()
                .map(d -> d.file().getName())
                .sorted()
                .collect(Collectors.toList());
        assertEquals("dept-alpha\nteam-alpha", Join.join("\n", names));
    }

    // ---------------------------------------------------------------
    // Type-level @Match
    // ---------------------------------------------------------------

    @Test
    public void typeLevelMatch() throws Exception {
        final TypeLevelReturns returns = root().assets().file().as(TypeLevelReturns.class);
        final List<String> names = returns.cssAssets()
                .map(a -> a.file().getName())
                .collect(Collectors.toList());
        assertEquals("main.css\nreset.css", Join.join("\n", names));
    }

    // ---------------------------------------------------------------
    // Type-level @Match validates single-arg input
    // ---------------------------------------------------------------

    @Test
    public void typeLevelMatchValidatesInput() throws Exception {
        final SingleArgReturns returns = root().assets().file().as(SingleArgReturns.class);
        final CssAsset css = returns.asset("main.css");
        assertEquals("assets/main.css", css.toString());
    }

    @Test
    public void typeLevelMatchRejectsInput() throws Exception {
        final SingleArgReturns returns = root().assets().file().as(SingleArgReturns.class);
        assertThrows(IllegalArgumentException.class, () -> returns.asset("app.js"));
    }

    // ---------------------------------------------------------------
    // Regex alternation
    // ---------------------------------------------------------------

    @Test
    public void regexAlternation() throws Exception {
        final AlternationReturns returns = root().assets().file().as(AlternationReturns.class);
        final List<S3File> data = returns.images().collect(Collectors.toList());
        assertEquals("hero.jpg\nlogo.png", Join.join("\n", S3File::getName, data));
    }

    // ---------------------------------------------------------------
    // Full-match semantics verification
    // ---------------------------------------------------------------

    @Test
    public void fullMatchSemantics() throws Exception {
        final FullMatchReturns returns = root().assets().file().as(FullMatchReturns.class);
        final List<S3File> data = returns.cssExact().collect(Collectors.toList());
        assertEquals("", Join.join("\n", S3File::getName, data));
    }

    // ---------------------------------------------------------------
    // Exclude tests
    // ---------------------------------------------------------------

    @Test
    public void excludeByRegex() throws Exception {
        final ExcludeReturns returns = root().groups().file().as(ExcludeReturns.class);
        final List<String> names = returns.nonAlphaDirs()
                .map(d -> d.file().getName())
                .sorted()
                .collect(Collectors.toList());
        assertEquals("dept-beta\norg-gamma\nteam-beta", Join.join("\n", names));
    }

    @Test
    public void includeAndExclude() throws Exception {
        final ExcludeReturns returns = root().assets().file().as(ExcludeReturns.class);
        final List<S3File> data = returns.cssExceptReset().collect(Collectors.toList());
        assertEquals("main.css", Join.join("\n", S3File::getName, data));
    }

    // ---------------------------------------------------------------
    // Filter ordering: @Filter only sees post-@Suffix-and-@Match entries
    // ---------------------------------------------------------------

    @Test
    public void filterOnlySeesPostSuffixAndMatch() throws Exception {
        RecordingFilter.seen.clear();

        final FilterOrderReturns returns = root().assets().file().as(FilterOrderReturns.class);
        returns.suffixMatchThenFilter().collect(Collectors.toList());

        // @Suffix(".css") narrows 7 assets to 2: main.css, reset.css
        // @Match("m.*") narrows to 1: main.css
        // @Filter should only see main.css
        assertEquals("main.css", Join.join("\n", RecordingFilter.seen));
    }

    @Test
    public void filterOnlySeesPostExcludeEntries() throws Exception {
        RecordingFilter.seen.clear();

        final FilterOrderReturns returns = root().assets().file().as(FilterOrderReturns.class);
        returns.excludeThenFilter().collect(Collectors.toList());

        // @Match(".*\\.css") includes main.css, reset.css
        // @Match(value = "reset\\.css", exclude = true) removes reset.css
        // @Filter should only see main.css
        assertEquals("main.css", Join.join("\n", RecordingFilter.seen));
    }

    // ---------------------------------------------------------------
    // Test interfaces
    // ---------------------------------------------------------------

    public interface Root extends S3.Dir {
        Assets assets();
        Groups groups();
    }

    public interface Assets extends S3.Dir {
    }

    public interface Groups extends S3.Dir {
    }

    public interface Asset extends S3 {
    }

    public interface MatchReturns {
        @Match(".*\\.css") Asset[] returnArrayOfInterface();
        @Match(".*\\.css") S3File[] returnArrayOfS3File();
        @Match(".*\\.css") Stream<Asset> returnStreamOfInterface();
        @Match(".*\\.css") Stream<S3File> returnStreamOfS3File();
        @Match(".*\\.css") List<Asset> returnListOfInterface();
        @Match(".*\\.css") List<S3File> returnListOfS3File();
        @Match(".*\\.css") Set<Asset> returnSetOfInterface();
        @Match(".*\\.css") Set<S3File> returnSetOfS3File();
        @Match(".*\\.css") Collection<Asset> returnCollectionOfInterface();
        @Match(".*\\.css") Collection<S3File> returnCollectionOfS3File();
    }

    public interface AssetFile extends S3.File {
    }

    public interface FileReturns extends S3.Dir {
        @Match(".*\\.css") Stream<S3.File> cssS3Files();
        @Match(".*\\.css") Stream<AssetFile> cssAssetFiles();
    }

    public interface GroupDir extends S3.Dir {
    }

    public interface DirReturns extends S3.Dir {
        @Match(".*-alpha") Stream<S3.Dir> alphaS3Dirs();
        @Match(".*-alpha") Stream<GroupDir> alphaGroupDirs();
    }

    @Match(".*\\.css")
    public interface CssAsset extends S3 {
    }

    public interface TypeLevelReturns {
        Stream<CssAsset> cssAssets();
    }

    public interface SingleArgReturns {
        CssAsset asset(String name);
    }

    public interface AlternationReturns {
        @Match(".*\\.(jpg|png)") Stream<S3File> images();
    }

    public interface FullMatchReturns {
        @Match("css") Stream<S3File> cssExact();
    }

    public interface ExcludeReturns {
        @Match(value = ".*-alpha", exclude = true)
        Stream<S3.Dir> nonAlphaDirs();

        @Match(".*\\.css")
        @Match(value = "reset\\.css", exclude = true)
        Stream<S3File> cssExceptReset();
    }

    public interface FilterOrderReturns {
        @Suffix(".css")
        @Match("m.*")
        @Filter(RecordingFilter.class)
        Stream<S3File> suffixMatchThenFilter();

        @Match(".*\\.css")
        @Match(value = "reset\\.css", exclude = true)
        @Filter(RecordingFilter.class)
        Stream<S3File> excludeThenFilter();
    }

    public static class RecordingFilter implements Predicate<S3File> {
        static final List<String> seen = new ArrayList<>();

        @Override
        public boolean test(final S3File s3File) {
            seen.add(s3File.getName());
            return true;
        }
    }
}
