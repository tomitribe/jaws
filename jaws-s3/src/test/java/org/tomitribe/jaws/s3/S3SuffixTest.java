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

public class S3SuffixTest {

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
    // All 10 return types with @Suffix
    // ---------------------------------------------------------------

    @Test
    public void returnArrayOfInterface() throws Exception {
        final SuffixReturns returns = root().assets().file().as(SuffixReturns.class);
        final Asset[] data = returns.returnArrayOfInterface();
        assertEquals("assets/main.css\nassets/reset.css", Join.join("\n", (Object[]) data));
    }

    @Test
    public void returnArrayOfS3File() throws Exception {
        final SuffixReturns returns = root().assets().file().as(SuffixReturns.class);
        final S3File[] data = returns.returnArrayOfS3File();
        assertEquals("main.css\nreset.css", Join.join("\n", S3File::getName, data));
    }

    @Test
    public void returnStreamOfInterface() throws Exception {
        final SuffixReturns returns = root().assets().file().as(SuffixReturns.class);
        final List<Asset> data = returns.returnStreamOfInterface().collect(Collectors.toList());
        assertEquals("assets/main.css\nassets/reset.css", Join.join("\n", data));
    }

    @Test
    public void returnStreamOfS3File() throws Exception {
        final SuffixReturns returns = root().assets().file().as(SuffixReturns.class);
        final List<S3File> data = returns.returnStreamOfS3File().collect(Collectors.toList());
        assertEquals("main.css\nreset.css", Join.join("\n", S3File::getName, data));
    }

    @Test
    public void returnListOfInterface() throws Exception {
        final SuffixReturns returns = root().assets().file().as(SuffixReturns.class);
        final List<Asset> data = returns.returnListOfInterface();
        assertEquals("assets/main.css\nassets/reset.css", Join.join("\n", data));
    }

    @Test
    public void returnListOfS3File() throws Exception {
        final SuffixReturns returns = root().assets().file().as(SuffixReturns.class);
        final List<S3File> data = returns.returnListOfS3File();
        assertEquals("main.css\nreset.css", Join.join("\n", S3File::getName, data));
    }

    @Test
    public void returnSetOfInterface() throws Exception {
        final SuffixReturns returns = root().assets().file().as(SuffixReturns.class);
        final List<String> data = returns.returnSetOfInterface().stream()
                .map(Asset::toString).sorted().collect(Collectors.toList());
        assertEquals("assets/main.css\nassets/reset.css", Join.join("\n", data));
    }

    @Test
    public void returnSetOfS3File() throws Exception {
        final SuffixReturns returns = root().assets().file().as(SuffixReturns.class);
        final List<String> data = returns.returnSetOfS3File().stream()
                .map(S3File::getName).sorted().collect(Collectors.toList());
        assertEquals("main.css\nreset.css", Join.join("\n", data));
    }

    @Test
    public void returnCollectionOfInterface() throws Exception {
        final SuffixReturns returns = root().assets().file().as(SuffixReturns.class);
        final Collection<Asset> data = returns.returnCollectionOfInterface();
        assertEquals("assets/main.css\nassets/reset.css", Join.join("\n", data));
    }

    @Test
    public void returnCollectionOfS3File() throws Exception {
        final SuffixReturns returns = root().assets().file().as(SuffixReturns.class);
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
    // Type-level @Suffix
    // ---------------------------------------------------------------

    @Test
    public void typeLevelSuffix() throws Exception {
        final TypeLevelReturns returns = root().assets().file().as(TypeLevelReturns.class);
        final List<String> names = returns.cssAssets()
                .map(a -> a.file().getName())
                .collect(Collectors.toList());
        assertEquals("main.css\nreset.css", Join.join("\n", names));
    }

    // ---------------------------------------------------------------
    // Multi-value @Suffix
    // ---------------------------------------------------------------

    @Test
    public void multiValueSuffix() throws Exception {
        final MultiValueReturns returns = root().assets().file().as(MultiValueReturns.class);
        final List<S3File> data = returns.images().collect(Collectors.toList());
        assertEquals("hero.jpg\nlogo.png", Join.join("\n", S3File::getName, data));
    }

    // ---------------------------------------------------------------
    // Exclude tests
    // ---------------------------------------------------------------

    @Test
    public void excludeSuffix() throws Exception {
        final ExcludeReturns returns = root().assets().file().as(ExcludeReturns.class);
        final List<S3File> data = returns.noJavaScript().collect(Collectors.toList());
        assertEquals("hero.jpg\nindex.html\nlogo.png\nmain.css\nreset.css", Join.join("\n", S3File::getName, data));
    }

    @Test
    public void includeAndExcludeSuffix() throws Exception {
        final ExcludeReturns returns = root().assets().file().as(ExcludeReturns.class);
        final List<S3File> data = returns.cssExceptReset().collect(Collectors.toList());
        assertEquals("main.css", Join.join("\n", S3File::getName, data));
    }

    // ---------------------------------------------------------------
    // Filter ordering: @Filter only sees post-@Suffix entries
    // ---------------------------------------------------------------

    @Test
    public void filterOnlySeesPostSuffix() throws Exception {
        RecordingFilter.seen.clear();

        final FilterOrderReturns returns = root().assets().file().as(FilterOrderReturns.class);
        returns.suffixThenFilter().collect(Collectors.toList());

        // The filter should only have seen the 2 CSS files, not all 7 assets
        assertEquals("main.css\nreset.css", Join.join("\n", RecordingFilter.seen));
    }

    @Test
    public void filterOnlySeesPostExcludeEntries() throws Exception {
        RecordingFilter.seen.clear();

        final FilterOrderReturns returns = root().assets().file().as(FilterOrderReturns.class);
        returns.excludeThenFilter().collect(Collectors.toList());

        // @Suffix(".css") includes main.css, reset.css
        // @Suffix(value = "reset.css", exclude = true) removes reset.css
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

    public interface SuffixReturns {
        @Suffix(".css") Asset[] returnArrayOfInterface();
        @Suffix(".css") S3File[] returnArrayOfS3File();
        @Suffix(".css") Stream<Asset> returnStreamOfInterface();
        @Suffix(".css") Stream<S3File> returnStreamOfS3File();
        @Suffix(".css") List<Asset> returnListOfInterface();
        @Suffix(".css") List<S3File> returnListOfS3File();
        @Suffix(".css") Set<Asset> returnSetOfInterface();
        @Suffix(".css") Set<S3File> returnSetOfS3File();
        @Suffix(".css") Collection<Asset> returnCollectionOfInterface();
        @Suffix(".css") Collection<S3File> returnCollectionOfS3File();
    }

    public interface AssetFile extends S3.File {
    }

    public interface FileReturns extends S3.Dir {
        @Suffix(".css") Stream<S3.File> cssS3Files();
        @Suffix(".css") Stream<AssetFile> cssAssetFiles();
    }

    public interface GroupDir extends S3.Dir {
    }

    public interface DirReturns extends S3.Dir {
        @Suffix("-alpha") Stream<S3.Dir> alphaS3Dirs();
        @Suffix("-alpha") Stream<GroupDir> alphaGroupDirs();
    }

    @Suffix(".css")
    public interface CssAsset extends S3 {
    }

    public interface TypeLevelReturns {
        Stream<CssAsset> cssAssets();
    }

    public interface MultiValueReturns {
        @Suffix({".jpg", ".png"}) Stream<S3File> images();
    }

    public interface ExcludeReturns {
        @Suffix(value = ".js", exclude = true)
        Stream<S3File> noJavaScript();

        @Suffix(".css")
        @Suffix(value = "reset.css", exclude = true)
        Stream<S3File> cssExceptReset();
    }

    public interface FilterOrderReturns {
        @Suffix(".css")
        @Filter(RecordingFilter.class)
        Stream<S3File> suffixThenFilter();

        @Suffix(".css")
        @Suffix(value = "reset.css", exclude = true)
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
