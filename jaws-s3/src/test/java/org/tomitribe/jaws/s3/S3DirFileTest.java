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
import org.tomitribe.util.IO;
import org.tomitribe.util.Join;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class S3DirFileTest {

    @Rule
    public MockS3Rule mockS3 = new MockS3Rule();
    private S3Client s3Client;

    @Before
    public final void setUp() throws Exception {
        final File store = mockS3.getBlobStoreLocation();
        this.s3Client = new S3Client(mockS3.getS3Client());

        new Archive()
                .add("colors/red/light.txt", "rose")
                .add("colors/red/dark.txt", "crimson")
                .add("colors/green/light.txt", "lime")
                .add("colors/green/dark.txt", "forest")
                .add("colors/blue/light.txt", "sky")
                .add("colors/blue/dark.txt", "navy")
                .add("colors/readme.txt", "about colors")
                .toDir(store);
    }

    // ---------------------------------------------------------------
    // S3 base methods
    // ---------------------------------------------------------------

    /**
     * S3.file() returns the underlying S3File for the proxy
     */
    @Test
    public void s3File() throws Exception {
        final ColorPalette palette = S3.of(ColorPalette.class, s3Client.getBucket("colors").asFile());
        final S3File file = palette.file();

        assertNotNull(file);
        assertEquals("", file.getAbsoluteName());
    }

    /**
     * S3.parent() returns the parent S3File
     */
    @Test
    public void s3Parent() throws Exception {
        final ColorPalette palette = S3.of(ColorPalette.class, s3Client.getBucket("colors").asFile());
        final ColorGroup red = palette.colorGroup("red");
        final S3File parent = red.parent();

        assertNotNull(parent);
        assertEquals(palette.file(), parent);
    }

    // ---------------------------------------------------------------
    // S3.Dir methods
    // ---------------------------------------------------------------

    /**
     * S3.Dir.file(String) returns a child S3File by name
     */
    @Test
    public void dirFile() throws Exception {
        final ColorPalette palette = S3.of(ColorPalette.class, s3Client.getBucket("colors").asFile());
        final S3File file = palette.file("readme.txt");

        assertNotNull(file);
        assertEquals("readme.txt", file.getAbsoluteName());
        assertEquals("about colors", file.getValueAsString());
    }

    /**
     * S3.Dir.files() returns all objects recursively (flat listing)
     */
    @Test
    public void dirFiles() throws Exception {
        final ColorPalette palette = S3.of(ColorPalette.class, s3Client.getBucket("colors").asFile());

        final List<String> list = palette.files()
                .map(S3File::getAbsoluteName)
                .sorted()
                .collect(Collectors.toList());

        assertEquals("" +
                "blue\n" +
                "blue/dark.txt\n" +
                "blue/light.txt\n" +
                "green\n" +
                "green/dark.txt\n" +
                "green/light.txt\n" +
                "readme.txt\n" +
                "red\n" +
                "red/dark.txt\n" +
                "red/light.txt", Join.join("\n", list));
    }

    /**
     * S3.Dir.list() returns immediate children (one level deep)
     */
    @Test
    public void dirList() throws Exception {
        final ColorPalette palette = S3.of(ColorPalette.class, s3Client.getBucket("colors").asFile());

        final List<String> list = palette.list()
                .map(S3File::getAbsoluteName)
                .sorted()
                .collect(Collectors.toList());

        assertEquals("blue\ngreen\nreadme.txt\nred", Join.join("\n", list));
    }

    // ---------------------------------------------------------------
    // S3.File methods
    // ---------------------------------------------------------------

    /**
     * S3.File.getValueAsStream()
     */
    @Test
    public void fileGetValueAsStream() throws Exception {
        final ColorFile colorFile = colorFile("readme.txt");

        try (final InputStream in = colorFile.getValueAsStream()) {
            assertEquals("about colors", IO.slurp(in));
        }
    }

    /**
     * S3.File.getValueAsString()
     */
    @Test
    public void fileGetValueAsString() throws Exception {
        final ColorFile colorFile = colorFile("readme.txt");

        assertEquals("about colors", colorFile.getValueAsString());
    }

    /**
     * S3.File.setValueAsString(String)
     */
    @Test
    public void fileSetValueAsString() throws Exception {
        final ColorFile colorFile = colorFile("readme.txt");

        colorFile.setValueAsString("updated");
        assertEquals("updated", colorFile.getValueAsString());
    }

    /**
     * S3.File.setValueAsStream(InputStream)
     */
    @Test
    public void fileSetValueAsStream() throws Exception {
        final ColorFile colorFile = colorFile("readme.txt");

        colorFile.setValueAsStream(new ByteArrayInputStream("streamed".getBytes()));
        assertEquals("streamed", colorFile.getValueAsString());
    }

    /**
     * S3.File.setValueAsFile(File)
     */
    @Test
    public void fileSetValueAsFile() throws Exception {
        final ColorFile colorFile = colorFile("readme.txt");

        final File tmp = File.createTempFile("s3test", ".txt");
        tmp.deleteOnExit();
        IO.copy(new ByteArrayInputStream("from file".getBytes()), tmp);

        colorFile.setValueAsFile(tmp);
        assertEquals("from file", colorFile.getValueAsString());
    }

    /**
     * S3.File.getETag()
     */
    @Test
    public void fileGetETag() throws Exception {
        final ColorFile colorFile = colorFile("readme.txt");

        // trigger metadata fetch
        colorFile.getValueAsString();
        final String eTag = colorFile.getETag();
        assertNotNull(eTag);
        assertTrue(eTag.length() > 0);
    }

    /**
     * S3.File.getSize()
     */
    @Test
    public void fileGetSize() throws Exception {
        final ColorFile colorFile = colorFile("readme.txt");

        assertEquals(12, colorFile.getSize());
    }

    /**
     * S3.File.getLastModified()
     */
    @Test
    public void fileGetLastModified() throws Exception {
        final ColorFile colorFile = colorFile("readme.txt");

        assertNotNull(colorFile.getLastModified());
    }

    /**
     * S3.File.getObjectMetadata()
     */
    @Test
    public void fileGetObjectMetadata() throws Exception {
        final ColorFile colorFile = colorFile("readme.txt");

        final ObjectMetadata metadata = colorFile.getObjectMetadata();
        assertNotNull(metadata);
        assertEquals(12, metadata.getContentLength());
    }

    // ---------------------------------------------------------------
    // Return type dispatch
    // ---------------------------------------------------------------

    /**
     * Stream<X> where X extends S3.Dir should only return directories
     */
    @Test
    public void streamOfDir() throws Exception {
        final ColorPalette palette = S3.of(ColorPalette.class, s3Client.getBucket("colors").asFile());

        final List<String> list = palette.colorGroups()
                .map(ColorGroup::toString)
                .sorted()
                .collect(Collectors.toList());

        assertEquals("blue\ngreen\nred", Join.join("\n", list));
    }

    /**
     * Stream<X> where X extends S3.File should only return files
     */
    @Test
    public void streamOfFile() throws Exception {
        final ColorPalette palette = S3.of(ColorPalette.class, s3Client.getBucket("colors").asFile());

        final List<String> list = palette.topLevelFiles()
                .map(f -> f.file().getAbsoluteName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("readme.txt", Join.join("\n", list));
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private ColorFile colorFile(final String key) {
        final S3Bucket bucket = s3Client.getBucket("colors");
        return S3.of(ColorFile.class, bucket.getFile(key));
    }

    // ---------------------------------------------------------------
    // Test interfaces
    // ---------------------------------------------------------------

    public interface ColorPalette extends S3.Dir {
        Stream<ColorGroup> colorGroups();

        Stream<ColorFile> topLevelFiles();

        ColorGroup colorGroup(String name);
    }

    public interface ColorGroup extends S3.Dir {
    }

    public interface ColorFile extends S3.File {
    }
}
