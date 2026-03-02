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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.tomitribe.jaws.s3.Filter;
import org.tomitribe.jaws.s3.MockS3Rule;
import org.tomitribe.jaws.s3.Name;
import org.tomitribe.jaws.s3.S3;
import org.tomitribe.jaws.s3.S3Client;
import org.tomitribe.jaws.s3.S3File;
import org.tomitribe.jaws.s3.Walk;
import org.tomitribe.util.Archive;
import org.tomitribe.util.Join;

import java.io.File;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

/**
 * Tests modeled after the Static Website example in docs/examples/static-website.md.
 *
 * Verifies that:
 * - @Walk Stream<AssetDir> layout() returns both directories and files
 * - Stream<Asset> assets() without @Walk returns only top-level files
 * - Stream<CssFile> stylesheets() scoped to CssDir returns only css files
 */
public class StaticWebsiteTest {

    @Rule
    public MockS3Rule mockS3 = new MockS3Rule();
    private S3Client s3Client;

    @Before
    public final void setUp() throws Exception {
        final File store = mockS3.getBlobStoreLocation();
        this.s3Client = new S3Client(mockS3.getS3Client());

        new Archive()
                .add("site/20250301-143022/index.html", "<html>hello</html>")
                .add("site/20250301-143022/manifest.json", "{\"version\":\"1.4.2\"}")
                .add("site/20250301-143022/css/main.css", "body{}")
                .add("site/20250301-143022/css/reset.css", "*{margin:0}")
                .add("site/20250301-143022/js/app.js", "console.log('hi')")
                .add("site/20250301-143022/js/vendor.js", "/* vendor */")
                .add("site/20250301-143022/images/logo.png", "PNG")
                .add("site/20250301-143022/images/hero.jpg", "JPEG")
                .toDir(store);
    }

    /**
     * @Walk Stream<AssetDir> layout() should return only directories
     * from the recursive walk, not files.
     */
    @Test
    public void walkLayout() throws Exception {
        final Deployment deploy = s3Client.getBucket("site")
                .as(Deployments.class)
                .deployment("20250301-143022");

        final List<String> paths = deploy.layout()
                .map(entry -> entry.file().getAbsoluteName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("" +
                "20250301-143022/css\n" +
                "20250301-143022/images\n" +
                "20250301-143022/js", Join.join("\n", paths));
    }

    /**
     * Stream<Asset> assets() without @Walk should use delimiter-based listing
     * and return only the immediate files (index.html, manifest.json),
     * not files inside css/, js/, or images/.
     */
    @Test
    public void assetsTopLevelOnly() throws Exception {
        final Deployment deploy = s3Client.getBucket("site")
                .as(Deployments.class)
                .deployment("20250301-143022");

        final List<String> paths = deploy.assets()
                .map(asset -> asset.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("index.html\nmanifest.json", Join.join("\n", paths));
    }

    /**
     * CssDir.stylesheets() should return only css files scoped to the css/ directory.
     * The type-level @Filter(IsCss.class) on CssFile adds an extra layer of safety.
     */
    @Test
    public void cssDirStylesheets() throws Exception {
        final Deployment deploy = s3Client.getBucket("site")
                .as(Deployments.class)
                .deployment("20250301-143022");

        final List<String> paths = deploy.css().stylesheets()
                .map(css -> css.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("main.css\nreset.css", Join.join("\n", paths));
    }

    /**
     * JsDir.scripts() should return only js files scoped to the js/ directory.
     */
    @Test
    public void jsDirScripts() throws Exception {
        final Deployment deploy = s3Client.getBucket("site")
                .as(Deployments.class)
                .deployment("20250301-143022");

        final List<String> paths = deploy.js().scripts()
                .map(js -> js.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("app.js\nvendor.js", Join.join("\n", paths));
    }

    // ---------------------------------------------------------------
    // Test interfaces — modeled after docs/examples/static-website.md
    // ---------------------------------------------------------------

    public interface Deployments extends S3.Dir {
        Stream<Deployment> deployments();
        Deployment deployment(String timestamp);
    }

    public interface Deployment extends S3.Dir {
        @Name("index.html")
        HtmlFile indexHtml();

        @Name("manifest.json")
        Manifest manifest();

        CssDir css();
        JsDir js();
        ImagesDir images();

        Stream<Asset> assets();

        @Walk
        Stream<S3.Dir> layout();
    }

    public interface CssDir extends S3.Dir {
        Stream<CssFile> stylesheets();
    }

    public interface JsDir extends S3.Dir {
        Stream<JsFile> scripts();
    }

    public interface ImagesDir extends S3.Dir {
        Stream<ImageFile> images();
    }

    public interface Manifest extends S3.File {
    }

    public interface Asset extends S3.File {
    }

    @Filter(IsCss.class)
    public interface CssFile extends S3.File {
    }

    @Filter(IsJs.class)
    public interface JsFile extends S3.File {
    }

    @Filter(IsImage.class)
    public interface ImageFile extends S3.File {
    }

    @Filter(IsHtml.class)
    public interface HtmlFile extends S3.File {
    }

    // ---------------------------------------------------------------
    // Filter predicates
    // ---------------------------------------------------------------

    public static class IsCss implements Predicate<S3File> {
        @Override
        public boolean test(final S3File file) {
            return file.getName().endsWith(".css");
        }
    }

    public static class IsJs implements Predicate<S3File> {
        @Override
        public boolean test(final S3File file) {
            return file.getName().endsWith(".js");
        }
    }

    public static class IsHtml implements Predicate<S3File> {
        @Override
        public boolean test(final S3File file) {
            final String name = file.getName();
            return name.endsWith(".htm")
                    || name.endsWith(".html");
        }
    }

    public static class IsImage implements Predicate<S3File> {
        @Override
        public boolean test(final S3File file) {
            final String name = file.getName();
            return name.endsWith(".png")
                    || name.endsWith(".jpg")
                    || name.endsWith(".gif")
                    || name.endsWith(".svg");
        }
    }
}
