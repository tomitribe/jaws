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

import com.amazonaws.services.s3.model.S3Object;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.tomitribe.util.Archive;
import org.tomitribe.util.dir.Name;

import java.io.File;

public class S3Test {

    @Rule
    public MockS3 mockS3 = new MockS3();
    private File store;
    private S3Client s3Client;


    @Before
    public final void setUp() throws Exception {
        this.store = mockS3.getBlobStoreLocation();
        this.s3Client = new S3Client(mockS3.getAmazonS3());
    }

    @Test
    public void test() throws Exception {
        new Archive()
                .add("project/src/main/java/foo.txt", "")
                .add("project/src/main/resources/foo.txt", "")
                .add("project/src/test/java/foo.txt", "")
                .add("project/src/test/resources/foo.txt", "")
                .add("project/target/foo.txt", "")
                .add("project/pom.xml", "")
                .toDir(store);

        final Project project = S3.of(Project.class, s3Client.getBucket("project").asFile());

//        project.
//
//        final S3Bucket bucket = s3Client.getBucket("repository");
//        final File dir = Files.tmpdir();
//        Files.mkdirs(dir, "src", "main", "java");
//        Files.mkdirs(dir, "src", "main", "resources");
//        Files.mkdirs(dir, "src", "test", "java");
//        Files.mkdirs(dir, "src", "test", "resources");
//        Files.mkdirs(dir, "target");
//        Files.file(dir, "pom.xml").createNewFile();
//
//        final Project project = Dir.of(Project.class, dir);
//
//        assertFiles(dir, "src/main/java", project.src().main().java());
//        assertFiles(dir, "src/main/resources", project.src().main().resources());
//        assertFiles(dir, "src/test/java", project.src().test().java());
//        assertFiles(dir, "src/test/resources", project.src().test().resources());
//        assertFiles(dir, "pom.xml", project.pomXml());
//        assertFiles(dir, "target", project.target());
    }

    public interface Project {
        Src src();

        S3File target();

        @Name("pom.xml")
        S3Object pomXml();
    }

    public interface Src {
        Section main();

        Section test();

        Section section(final String name);
    }

    public interface Section {
        S3File java();

        S3File resources();
    }
}
