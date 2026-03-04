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

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class S3RecursiveTest {

    @RegisterExtension
    public MockS3Extension mockS3 = new MockS3Extension();
    private S3Client s3Client;

    @BeforeEach
    public final void setUp() throws Exception {
        this.s3Client = new S3Client(mockS3.getS3Client());
    }

    @Test
    public void recursiveStreamOfS3File() throws Exception {
        final S3Bucket bucket = s3Client.createBucket("repository")
                .put("org.color/red/1/1.4/foo.txt", "")
                .put("org.color.bright/green/1/1.4/foo.txt", "")
                .put("junit/junit/4/4.12/bar.txt", "")
                .put("io.tomitribe/crest/5/5.4.1.2/baz.txt", "");
        final Work work = bucket.as(Work.class);

        final List<S3File> list = work.recursiveFiles().collect(Collectors.toList());

        final List<String> paths = paths(list);

        // @Recursive Stream<S3File> uses files() — flat listing of all descendant objects
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
    }

    @Test
    public void recursiveStreamOfDir() throws Exception {
        final S3Bucket bucket = s3Client.createBucket("repository")
                .put("org.color/red/1/1.4/foo.txt", "")
                .put("org.color.bright/green/1/1.4/foo.txt", "")
                .put("junit/junit/4/4.12/bar.txt", "")
                .put("io.tomitribe/crest/5/5.4.1.2/baz.txt", "");
        final Work work = bucket.as(Work.class);

        final List<S3File> list = work.recursiveDirs()
                .map(DirType::file)
                .collect(Collectors.toList());

        final List<String> paths = paths(list);

        assertEquals("" +
                "io.tomitribe/\n" +
                "io.tomitribe/crest/\n" +
                "io.tomitribe/crest/5/\n" +
                "io.tomitribe/crest/5/5.4.1.2/\n" +
                "junit/\n" +
                "junit/junit/\n" +
                "junit/junit/4/\n" +
                "junit/junit/4/4.12/\n" +
                "org.color.bright/\n" +
                "org.color.bright/green/\n" +
                "org.color.bright/green/1/\n" +
                "org.color.bright/green/1/1.4/\n" +
                "org.color/\n" +
                "org.color/red/\n" +
                "org.color/red/1/\n" +
                "org.color/red/1/1.4/", Join.join("\n", paths));
    }

    @Test
    public void recursiveWithPrefix() throws Exception {
        final S3Bucket bucket = s3Client.createBucket("repository")
                .put("org.color/red/1/1.4/foo.txt", "")
                .put("org.color.bright/green/1/1.4/foo.txt", "")
                .put("junit/junit/4/4.12/bar.txt", "")
                .put("io.tomitribe/crest/5/5.4.1.2/baz.txt", "");
        final Work work = bucket.as(Work.class);
        final List<S3File> list = work.recursiveWithPrefix().collect(Collectors.toList());

        final List<String> paths = paths(list);

        // @Recursive @Prefix("org.") uses files(request) — returns all objects with prefix "org."
        assertEquals("" +
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
    }

    @Test
    public void recursiveWithFilter() throws Exception {
        final S3Bucket bucket = s3Client.createBucket("repository")
                .put("src/main/java/Foo.java", "")
                .put("src/main/java/Bar.java", "")
                .put("src/main/resources/config.xml", "")
                .put("src/test/java/FooTest.java", "");
        final Work work = bucket.as(Work.class);
        final List<S3File> list = work.recursiveJavaFiles().collect(Collectors.toList());

        final List<String> paths = paths(list);

        assertEquals("" +
                "src/main/java/Bar.java\n" +
                "src/main/java/Foo.java\n" +
                "src/test/java/FooTest.java", Join.join("\n", paths));
    }

    @Test
    public void defaultStreamIsImmediate() throws Exception {
        final S3Bucket bucket = s3Client.createBucket("repository")
                .put("org.color/red/1/1.4/foo.txt", "")
                .put("org.color.bright/green/1/1.4/foo.txt", "")
                .put("junit/junit/4/4.12/bar.txt", "")
                .put("io.tomitribe/crest/5/5.4.1.2/baz.txt", "");
        final Work work = bucket.as(Work.class);

        final List<S3File> list = work.immediateFiles().collect(Collectors.toList());

        final List<String> paths = paths(list);

        // Without @Recursive, plain Stream<S3File> returns immediate files only
        // At the bucket root, there are no immediate files (only directories)
        assertEquals("", Join.join("\n", paths));
    }

    @Test
    public void defaultStreamOfDirIsImmediate() throws Exception {
        final S3Bucket bucket = s3Client.createBucket("repository")
                .put("org.color/red/1/1.4/foo.txt", "")
                .put("org.color.bright/green/1/1.4/foo.txt", "")
                .put("junit/junit/4/4.12/bar.txt", "")
                .put("io.tomitribe/crest/5/5.4.1.2/baz.txt", "");
        final Work work = bucket.as(Work.class);

        final List<S3File> list = work.immediateDirs()
                .map(DirType::file)
                .collect(Collectors.toList());

        final List<String> paths = paths(list);

        assertEquals("" +
                "io.tomitribe/\n" +
                "junit/\n" +
                "org.color.bright/\n" +
                "org.color/", Join.join("\n", paths));
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
    }

    private List<String> paths(final List<S3File> list) {
        return list.stream()
                .map(file -> file.isDirectory() ? file.getAbsoluteName() + "/" : file.getAbsoluteName())
                .sorted()
                .collect(Collectors.toList());
    }

    public interface Work extends S3.Dir {
        @Recursive
        Stream<S3File> recursiveFiles();

        @Recursive
        Stream<DirType> recursiveDirs();

        @Recursive
        @Prefix("org.")
        Stream<S3File> recursiveWithPrefix();

        @Recursive
        @Filter(IsJava.class)
        Stream<S3File> recursiveJavaFiles();

        Stream<S3File> immediateFiles();

        Stream<DirType> immediateDirs();
    }

    public interface DirType extends S3.Dir {
    }

    public static class IsJava implements Predicate<S3File> {
        @Override
        public boolean test(final S3File file) {
            return file.getName().endsWith(".java");
        }
    }

}
