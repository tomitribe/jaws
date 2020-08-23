/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.tomitribe.jaws.s3;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.tomitribe.util.Archive;
import org.tomitribe.util.Join;
import org.tomitribe.util.dir.Dir;
import org.tomitribe.util.dir.Walk;

import java.io.File;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class S3WalkTest {

    @Rule
    public MockS3 mockS3 = new MockS3();
    private S3Client s3Client;
    private File store;

    @Before
    public final void setUp() throws Exception {
        store = mockS3.getBlobStoreLocation();
        this.s3Client = new S3Client(mockS3.getAmazonS3());
    }

    @Test
    public void walk() throws Exception {
        new Archive()
                .add("repository/org.color/red/1/1.4/foo.txt", "")
                .add("repository/org.color.bright/green/1/1.4/foo.txt", "")
                .add("repository/junit/junit/4/4.12/bar.txt", "")
                .add("repository/io.tomitribe/crest/5/5.4.1.2/baz.txt", "")
                .toDir(store);

        final S3Bucket bucket = s3Client.getBucket("repository");
        final Work work = S3.of(Work.class, bucket.asFile());

        final List<S3File> list = work.nofilter().collect(Collectors.toList());

        final List<String> paths = paths(list);

        assertEquals("" +
                "io.tomitribe/\n" +
                "io.tomitribe/crest/\n" +
                "io.tomitribe/crest/5/\n" +
                "io.tomitribe/crest/5/5.4.1.2/\n" +
                "io.tomitribe/crest/5/5.4.1.2/baz.txt\n" +
                "junit/\n" +
                "junit/junit/\n" +
                "junit/junit/4/\n" +
                "junit/junit/4/4.12/\n" +
                "junit/junit/4/4.12/bar.txt\n" +
                "org.color.bright/\n" +
                "org.color.bright/green/\n" +
                "org.color.bright/green/1/\n" +
                "org.color.bright/green/1/1.4/\n" +
                "org.color.bright/green/1/1.4/foo.txt\n" +
                "org.color/\n" +
                "org.color/red/\n" +
                "org.color/red/1/\n" +
                "org.color/red/1/1.4/\n" +
                "org.color/red/1/1.4/foo.txt", Join.join("\n", paths));
    }

    @Test
    public void maxDepthOne() throws Exception {
        new Archive()
                .add("repository/org.color/red/1/1.4/foo.txt", "")
                .add("repository/org.color.bright/green/1/1.4/foo.txt", "")
                .add("repository/junit/junit/4/4.12/bar.txt", "")
                .add("repository/io.tomitribe/crest/5/5.4.1.2/baz.txt", "")
                .toDir(store);

        final S3Bucket bucket = s3Client.getBucket("repository");
        final Work work = S3.of(Work.class, bucket.asFile());
        final List<S3File> list = work.maxOne().collect(Collectors.toList());

        final List<String> paths = paths(list);

        assertEquals("" +
                "io.tomitribe/\n" +
                "junit/\n" +
                "org.color.bright/\n" +
                "org.color/", Join.join("\n", paths));
    }

    @Test
    public void maxDepthTwo() throws Exception {
        new Archive()
                .add("repository/org.color/red/1/1.4/foo.txt", "")
                .add("repository/org.color.bright/green/1/1.4/foo.txt", "")
                .add("repository/junit/junit/4/4.12/bar.txt", "")
                .add("repository/io.tomitribe/crest/5/5.4.1.2/baz.txt", "")
                .toDir(store);

        final S3Bucket bucket = s3Client.getBucket("repository");
        final Work work = S3.of(Work.class, bucket.asFile());
        final List<S3File> list = work.maxTwo().collect(Collectors.toList());

        final List<String> paths = paths(list);

        assertEquals("" +
                "io.tomitribe/\n" +
                "io.tomitribe/crest/\n" +
                "junit/\n" +
                "junit/junit/\n" +
                "org.color.bright/\n" +
                "org.color.bright/green/\n" +
                "org.color/\n" +
                "org.color/red/", Join.join("\n", paths));
    }

    @Test
    public void minDepthOne() throws Exception {
        new Archive()
                .add("repository/org.color/red/1/1.4/foo.txt", "")
                .add("repository/org.color.bright/green/1/1.4/foo.txt", "")
                .add("repository/junit/junit/4/4.12/bar.txt", "")
                .add("repository/io.tomitribe/crest/5/5.4.1.2/baz.txt", "")
                .toDir(store);

        final S3Bucket bucket = s3Client.getBucket("repository");
        final Work work = S3.of(Work.class, bucket.asFile());
        final List<S3File> list = work.minOne().collect(Collectors.toList());

        final List<String> paths = paths(list);

        assertEquals("" +
                "io.tomitribe/\n" +
                "io.tomitribe/crest/\n" +
                "io.tomitribe/crest/5/\n" +
                "io.tomitribe/crest/5/5.4.1.2/\n" +
                "io.tomitribe/crest/5/5.4.1.2/baz.txt\n" +
                "junit/\n" +
                "junit/junit/\n" +
                "junit/junit/4/\n" +
                "junit/junit/4/4.12/\n" +
                "junit/junit/4/4.12/bar.txt\n" +
                "org.color.bright/\n" +
                "org.color.bright/green/\n" +
                "org.color.bright/green/1/\n" +
                "org.color.bright/green/1/1.4/\n" +
                "org.color.bright/green/1/1.4/foo.txt\n" +
                "org.color/\n" +
                "org.color/red/\n" +
                "org.color/red/1/\n" +
                "org.color/red/1/1.4/\n" +
                "org.color/red/1/1.4/foo.txt", Join.join("\n", paths));
    }

    @Test
    public void minDepthTwo() throws Exception {
        new Archive()
                .add("repository/org.color/red/1/1.4/foo.txt", "")
                .add("repository/org.color.bright/green/1/1.4/foo.txt", "")
                .add("repository/junit/junit/4/4.12/bar.txt", "")
                .add("repository/io.tomitribe/crest/5/5.4.1.2/baz.txt", "")
                .toDir(store);

        final S3Bucket bucket = s3Client.getBucket("repository");
        final Work work = S3.of(Work.class, bucket.asFile());
        final List<S3File> list = work.minTwo().collect(Collectors.toList());

        final List<String> paths = paths(list);

        assertEquals("" +
                "io.tomitribe/crest/\n" +
                "io.tomitribe/crest/5/\n" +
                "io.tomitribe/crest/5/5.4.1.2/\n" +
                "io.tomitribe/crest/5/5.4.1.2/baz.txt\n" +
                "junit/junit/\n" +
                "junit/junit/4/\n" +
                "junit/junit/4/4.12/\n" +
                "junit/junit/4/4.12/bar.txt\n" +
                "org.color.bright/green/\n" +
                "org.color.bright/green/1/\n" +
                "org.color.bright/green/1/1.4/\n" +
                "org.color.bright/green/1/1.4/foo.txt\n" +
                "org.color/red/\n" +
                "org.color/red/1/\n" +
                "org.color/red/1/1.4/\n" +
                "org.color/red/1/1.4/foo.txt", Join.join("\n", paths));
    }

    @Test
    public void minOneMaxTwo() throws Exception {
        new Archive()
                .add("repository/org.color/red/1/1.4/foo.txt", "")
                .add("repository/org.color.bright/green/1/1.4/foo.txt", "")
                .add("repository/junit/junit/4/4.12/bar.txt", "")
                .add("repository/io.tomitribe/crest/5/5.4.1.2/baz.txt", "")
                .toDir(store);

        final S3Bucket bucket = s3Client.getBucket("repository");
        final Work work = S3.of(Work.class, bucket.asFile());
        final List<S3File> list = work.minOneMaxTwo().collect(Collectors.toList());

        final List<String> paths = paths(list);

        assertEquals("" +
                "io.tomitribe/\n" +
                "io.tomitribe/crest/\n" +
                "junit/\n" +
                "junit/junit/\n" +
                "org.color.bright/\n" +
                "org.color.bright/green/\n" +
                "org.color/\n" +
                "org.color/red/", Join.join("\n", paths));
    }

    @Test
    public void minTwoMaxTwo() throws Exception {
        new Archive()
                .add("repository/org.color/red/1/1.4/foo.txt", "")
                .add("repository/org.color.bright/green/1/1.4/foo.txt", "")
                .add("repository/junit/junit/4/4.12/bar.txt", "")
                .add("repository/io.tomitribe/crest/5/5.4.1.2/baz.txt", "")
                .toDir(store);

        final S3Bucket bucket = s3Client.getBucket("repository");
        final Work work = S3.of(Work.class, bucket.asFile());
        final List<S3File> list = work.minTwoMaxTwo().collect(Collectors.toList());

        final List<String> paths = paths(list);

        assertEquals("" +
                "io.tomitribe/crest/\n" +
                "junit/junit/\n" +
                "org.color.bright/green/\n" +
                "org.color/red/", Join.join("\n", paths));
    }

    @Test
    public void files() throws Exception {
        new Archive()
                .add("repository/org.color/red/1/1.4/foo.txt", "")
                .add("repository/org.color.bright/green/1/1.4/foo.txt", "")
                .add("repository/junit/junit/4/4.12/bar.txt", "")
                .add("repository/io.tomitribe/crest/5/5.4.1.2/baz.txt", "")
                .toDir(store);

        final S3Bucket bucket = s3Client.getBucket("repository");
        final Work work = S3.of(Work.class, bucket.asFile());
        final List<S3File> list = work.files().collect(Collectors.toList());

        final List<String> paths = paths(list);

        assertEquals("io.tomitribe/crest/5/5.4.1.2/baz.txt\n" +
                "junit/junit/4/4.12/bar.txt\n" +
                "org.color.bright/green/1/1.4/foo.txt\n" +
                "org.color/red/1/1.4/foo.txt", Join.join("\n", paths));
    }

    public String path(final File file) {
        if (file.isDirectory()) return file.getAbsolutePath() + "/";
        else return file.getAbsolutePath();
    }

    private List<String> paths(final List<S3File> list) {
        return list.stream()
                .map(file -> file.isDirectory() ? file.getAbsoluteName() + "/" : file.getAbsoluteName())
                .sorted()
                .collect(Collectors.toList());
    }

    public interface Work extends S3 {
        @Walk
        Stream<S3File> nofilter();

        @Walk(maxDepth = 1)
        Stream<S3File> maxOne();

        @Walk(maxDepth = 2)
        Stream<S3File> maxTwo();

        @Walk(minDepth = 1)
        Stream<S3File> minOne();

        @Walk(minDepth = 2)
        Stream<S3File> minTwo();

        @Walk(minDepth = 1, maxDepth = 2)
        Stream<S3File> minOneMaxTwo();

        @Walk(minDepth = 2, maxDepth = 2)
        Stream<S3File> minTwoMaxTwo();
    }

    public interface Module extends S3 {

        @Walk
        @Filter(IsJava.class)
        Stream<S3File> streamOfFiles();

        @Walk
        @Filter(IsJava.class)
        Stream<Java> streamOfJava();

        @Walk
        @Filter(IsJava.class)
        S3File[] arrayOfFiles();

        @Walk
        @Filter(IsJava.class)
        Java[] arrayOfJava();

    }

    public interface Java extends Dir {

    }

    public static class IsJava implements Predicate<S3File> {
        @Override
        public boolean test(final S3File file) {
            return file.getName().endsWith(".java");
        }
    }

}