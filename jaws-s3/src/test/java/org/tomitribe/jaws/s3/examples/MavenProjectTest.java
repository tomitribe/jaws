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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.tomitribe.jaws.s3.MockS3Extension;
import org.tomitribe.jaws.s3.Name;
import org.tomitribe.jaws.s3.Parent;
import org.tomitribe.jaws.s3.S3;
import org.tomitribe.jaws.s3.S3Client;
import org.tomitribe.util.Join;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests modeled after the Maven Project example in docs/examples/maven-project.md.
 *
 * Verifies @Name, @Parent, named child lookup, and Stream<Dir> dispatch.
 */
public class MavenProjectTest {

    @RegisterExtension
    public MockS3Extension mockS3 = new MockS3Extension();
    private S3Client s3Client;

    @BeforeEach
    public final void setUp() throws Exception {
        this.s3Client = new S3Client(mockS3.getS3Client());

        s3Client.createBucket("my-project")
                .put("pom.xml", "<project>parent</project>")
                .put("README.md", "# My Project")
                .put("core/pom.xml", "<project>core</project>")
                .put("core/src/main/java/App.java", "class App {}")
                .put("core/src/main/java/Config.java", "class Config {}")
                .put("core/src/main/resources/application.properties", "key=value")
                .put("core/src/test/java/AppTest.java", "class AppTest {}")
                .put("core/src/test/resources/test.properties", "test=true")
                .put("web/pom.xml", "<project>web</project>")
                .put("web/src/main/java/WebApp.java", "class WebApp {}")
                .put("web/src/main/java/Controller.java", "class Controller {}")
                .put("web/src/main/resources/templates/index.html", "<html/>")
                .put("web/src/test/java/WebAppTest.java", "class WebAppTest {}");
    }

    /**
     * Stream<Module> where Module extends S3.Dir should return only directories.
     */
    @Test
    public void listModules() throws Exception {
        final Project project = s3Client.getBucket("my-project").as(Project.class);

        final List<String> names = project.modules()
                .map(m -> m.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("core\nweb", Join.join("\n", names));
    }

    /**
     * module(String) should resolve the named child as a Module proxy.
     */
    @Test
    public void moduleByName() throws Exception {
        final Project project = s3Client.getBucket("my-project").as(Project.class);

        final Module core = project.module("core");
        assertEquals("core", core.file().getName());
    }

    /**
     * @Name("pom.xml") should resolve to the pom.xml key and read its content.
     */
    @Test
    public void readPomXml() throws Exception {
        final Project project = s3Client.getBucket("my-project").as(Project.class);

        assertEquals("<project>parent</project>", project.pomXml().getValueAsString());
        assertEquals("<project>core</project>", project.module("core").pomXml().getValueAsString());
    }

    /**
     * @Name("README.md") should resolve to the README.md key.
     */
    @Test
    public void readReadme() throws Exception {
        final Project project = s3Client.getBucket("my-project").as(Project.class);

        assertEquals("# My Project", project.readme().getValueAsString());
    }

    /**
     * @Parent on Module should navigate back to the Project.
     */
    @Test
    public void parentFromModule() throws Exception {
        final Project project = s3Client.getBucket("my-project").as(Project.class);
        final Module web = project.module("web");

        final Project root = web.project();
        assertEquals(project.file().getAbsoluteName(), root.file().getAbsoluteName());
    }

    /**
     * @Parent(2) on Section should navigate from section → src → module.
     */
    @Test
    public void parentFromSection() throws Exception {
        final Project project = s3Client.getBucket("my-project").as(Project.class);
        final Module web = project.module("web");
        final Section main = web.src().main();

        final Module parent = main.module();
        assertEquals("web", parent.file().getName());
    }

    /**
     * Stream<SourceFile> where SourceFile extends S3.File returns only files.
     */
    @Test
    public void listSources() throws Exception {
        final Project project = s3Client.getBucket("my-project").as(Project.class);
        final Module core = project.module("core");

        final List<String> names = core.src().main().java().sources()
                .map(f -> f.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("App.java\nConfig.java", Join.join("\n", names));
    }

    // ---------------------------------------------------------------
    // Test interfaces — modeled after docs/examples/maven-project.md
    // ---------------------------------------------------------------

    public interface Project extends S3.Dir {
        @Name("pom.xml")
        PomFile pomXml();

        @Name("README.md")
        ReadmeFile readme();

        Stream<Module> modules();

        Module module(String name);
    }

    public interface Module extends S3.Dir {
        @Name("pom.xml")
        PomFile pomXml();

        Src src();

        @Parent
        Project project();
    }

    public interface Src extends S3.Dir {
        Section main();
        Section test();
    }

    public interface Section extends S3.Dir {
        SourceDir java();
        SourceDir resources();

        @Parent(2)
        Module module();
    }

    public interface SourceDir extends S3.Dir {
        Stream<SourceFile> sources();
    }

    public interface SourceFile extends S3.File {
    }

    public interface PomFile extends S3.File {
    }

    public interface ReadmeFile extends S3.File {
    }
}
