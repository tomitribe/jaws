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
import org.tomitribe.util.Archive;
import org.tomitribe.util.Join;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class S3SuffixTest {

    @Rule
    public MockS3Rule mockS3 = new MockS3Rule();
    private S3Client s3Client;

    @Before
    public final void setUp() throws Exception {
        final File store = mockS3.getBlobStoreLocation();
        this.s3Client = new S3Client(mockS3.getS3Client());

        new Archive()
                .add("bucket/assets/main.css", "")
                .add("bucket/assets/reset.css", "")
                .add("bucket/assets/app.js", "")
                .add("bucket/assets/vendor.js", "")
                .add("bucket/assets/logo.png", "")
                .add("bucket/assets/hero.jpg", "")
                .add("bucket/assets/index.html", "")
                .add("bucket/groups/team-alpha/file1.txt", "")
                .add("bucket/groups/team-beta/file2.txt", "")
                .add("bucket/groups/dept-alpha/file3.txt", "")
                .add("bucket/groups/dept-beta/file4.txt", "")
                .add("bucket/groups/org-gamma/file5.txt", "")
                .toDir(store);
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
}
