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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.tomitribe.util.Join;
import org.tomitribe.util.dir.Dir;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class S3WalkTest {

    @RegisterExtension
    public MockS3Extension mockS3 = new MockS3Extension();
    private S3Client s3Client;

    @BeforeEach
    public final void setUp() throws Exception {
        this.s3Client = new S3Client(mockS3.getS3Client());
    }

    @Test
    public void walk() throws Exception {
        final S3Bucket bucket = s3Client.createBucket("repository")
                .put("org.color/red/1/1.4/foo.txt", "")
                .put("org.color.bright/green/1/1.4/foo.txt", "")
                .put("junit/junit/4/4.12/bar.txt", "")
                .put("io.tomitribe/crest/5/5.4.1.2/baz.txt", "");
        final Work work = bucket.as(Work.class);

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
        final S3Bucket bucket = s3Client.createBucket("repository")
                .put("org.color/red/1/1.4/foo.txt", "")
                .put("org.color.bright/green/1/1.4/foo.txt", "")
                .put("junit/junit/4/4.12/bar.txt", "")
                .put("io.tomitribe/crest/5/5.4.1.2/baz.txt", "");
        final Work work = bucket.as(Work.class);
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
        final S3Bucket bucket = s3Client.createBucket("repository")
                .put("org.color/red/1/1.4/foo.txt", "")
                .put("org.color.bright/green/1/1.4/foo.txt", "")
                .put("junit/junit/4/4.12/bar.txt", "")
                .put("io.tomitribe/crest/5/5.4.1.2/baz.txt", "");
        final Work work = bucket.as(Work.class);
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
        final S3Bucket bucket = s3Client.createBucket("repository")
                .put("org.color/red/1/1.4/foo.txt", "")
                .put("org.color.bright/green/1/1.4/foo.txt", "")
                .put("junit/junit/4/4.12/bar.txt", "")
                .put("io.tomitribe/crest/5/5.4.1.2/baz.txt", "");
        final Work work = bucket.as(Work.class);
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
        final S3Bucket bucket = s3Client.createBucket("repository")
                .put("org.color/red/1/1.4/foo.txt", "")
                .put("org.color.bright/green/1/1.4/foo.txt", "")
                .put("junit/junit/4/4.12/bar.txt", "")
                .put("io.tomitribe/crest/5/5.4.1.2/baz.txt", "");
        final Work work = bucket.as(Work.class);
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
        final S3Bucket bucket = s3Client.createBucket("repository")
                .put("org.color/red/1/1.4/foo.txt", "")
                .put("org.color.bright/green/1/1.4/foo.txt", "")
                .put("junit/junit/4/4.12/bar.txt", "")
                .put("io.tomitribe/crest/5/5.4.1.2/baz.txt", "");
        final Work work = bucket.as(Work.class);
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
        final S3Bucket bucket = s3Client.createBucket("repository")
                .put("org.color/red/1/1.4/foo.txt", "")
                .put("org.color.bright/green/1/1.4/foo.txt", "")
                .put("junit/junit/4/4.12/bar.txt", "")
                .put("io.tomitribe/crest/5/5.4.1.2/baz.txt", "");
        final Work work = bucket.as(Work.class);
        final List<S3File> list = work.minTwoMaxTwo().collect(Collectors.toList());

        final List<String> paths = paths(list);

        assertEquals("" +
                "io.tomitribe/crest/\n" +
                "junit/junit/\n" +
                "org.color.bright/green/\n" +
                "org.color/red/", Join.join("\n", paths));
    }

    @Test
    public void walkWithPrefix() throws Exception {
        final S3Bucket bucket = s3Client.createBucket("repository")
                .put("org.color/red/1/1.4/foo.txt", "")
                .put("org.color.bright/green/1/1.4/foo.txt", "")
                .put("junit/junit/4/4.12/bar.txt", "")
                .put("io.tomitribe/crest/5/5.4.1.2/baz.txt", "");
        final Work work = bucket.as(Work.class);
        final List<S3File> list = work.walkWithPrefix().collect(Collectors.toList());

        final List<String> paths = paths(list);

        assertEquals("" +
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
    public void walkWithPrefixMaxTwo() throws Exception {
        final S3Bucket bucket = s3Client.createBucket("repository")
                .put("org.color/red/1/1.4/foo.txt", "")
                .put("org.color.bright/green/1/1.4/foo.txt", "")
                .put("junit/junit/4/4.12/bar.txt", "")
                .put("io.tomitribe/crest/5/5.4.1.2/baz.txt", "");
        final Work work = bucket.as(Work.class);
        final List<S3File> list = work.walkWithPrefixMaxTwo().collect(Collectors.toList());

        final List<String> paths = paths(list);

        assertEquals("" +
                "org.color.bright/\n" +
                "org.color.bright/green/\n" +
                "org.color/\n" +
                "org.color/red/", Join.join("\n", paths));
    }

    @Test
    public void files() throws Exception {
        final S3Bucket bucket = s3Client.createBucket("repository")
                .put("org.color/red/1/1.4/foo.txt", "")
                .put("org.color.bright/green/1/1.4/foo.txt", "")
                .put("junit/junit/4/4.12/bar.txt", "")
                .put("io.tomitribe/crest/5/5.4.1.2/baz.txt", "");
        final Work work = bucket.as(Work.class);
        final List<S3File> list = work.files().collect(Collectors.toList());

        final List<String> paths = paths(list);

        assertEquals("io.tomitribe\n" +
                "io.tomitribe/crest\n" +
                "io.tomitribe/crest/5\n" +
                "io.tomitribe/crest/5/5.4.1.2\n" +
                "io.tomitribe/crest/5/5.4.1.2/baz.txt\n" +
                "junit\n" +
                "junit/junit\n" +
                "junit/junit/4\n" +
                "junit/junit/4/4.12\n" +
                "junit/junit/4/4.12/bar.txt\n" +
                "org.color\n" +
                "org.color.bright\n" +
                "org.color.bright/green\n" +
                "org.color.bright/green/1\n" +
                "org.color.bright/green/1/1.4\n" +
                "org.color.bright/green/1/1.4/foo.txt\n" +
                "org.color/red\n" +
                "org.color/red/1\n" +
                "org.color/red/1/1.4\n" +
                "org.color/red/1/1.4/foo.txt", Join.join("\n", paths));
//        assertEquals("io.tomitribe/crest/5/5.4.1.2/baz.txt\n" +
//                "junit/junit/4/4.12/bar.txt\n" +
//                "org.color.bright/green/1/1.4/foo.txt\n" +
//                "org.color/red/1/1.4/foo.txt", Join.join("\n", paths));
    }

    private List<String> paths(final List<S3File> list) {
        return list.stream()
                .map(file -> file.isDirectory() ? file.getAbsoluteName() + "/" : file.getAbsoluteName())
                .sorted()
                .collect(Collectors.toList());
    }

    public interface Work extends S3.Dir {
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

        @Walk
        @Prefix("org.")
        Stream<S3File> walkWithPrefix();

        @Walk(maxDepth = 2)
        @Prefix("org.")
        Stream<S3File> walkWithPrefixMaxTwo();
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