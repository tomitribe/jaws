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
import org.tomitribe.util.dir.Name;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

public class S3Test {

    @Rule
    public MockS3 mockS3 = new MockS3();
    private S3Client s3Client;
    private Project project;

    @Before
    public final void setUp() throws Exception {
        final File store = mockS3.getBlobStoreLocation();
        this.s3Client = new S3Client(mockS3.getAmazonS3());

        new Archive()
                .add("project/pom.xml", "<xml>")
                .add("project/src/main/java/org/supertribe/colors/Green.java", "forrest")
                .add("project/src/main/java/org/supertribe/colors/Red.java", "crimson")
                .add("project/src/main/resources/square.txt", "block")
                .add("project/src/main/resources/triangle.txt", "three")
                .add("project/src/test/java/org/supertribe/colors/GreenTest.java", "forrest test")
                .add("project/src/test/java/org/supertribe/colors/RedTest.java", "crimson test")
                .add("project/src/test/resources/circle.txt", "pizza")
                .add("project/src/test/resources/rectangle.txt", "book")
                .add("project/target/bar.txt", "barbar")
                .add("project/target/foo.txt", "foofoo")
                .toDir(store);

        project = S3.of(Project.class, s3Client.getBucket("project").asFile());
    }

    @Test
    public void objectToString() throws Exception {
        final Section main = project.src().main();
        assertEquals("src/main", main.toString());
    }

    @Test
    public void objectEquals() throws Exception {
        final Project project2 = S3.of(Project.class, s3Client.getBucket("project").asFile());

        assertEquals(project, project2);
        assertNotEquals(project, project.src());
        assertNotEquals(project, null);
    }

    @Test
    public void objectHashCode() throws Exception {
        final S3File s3File = project.get();
        assertEquals(project.hashCode(), s3File.hashCode());
    }

    @Test
    public void s3Get() throws Exception {
        final S3File expected = s3Client.getBucket("project").asFile();
        final S3File actual = this.project.get();
        assertEquals(expected, actual);
    }

    @Test
    public void s3Parent() throws Exception {
        final S3File expected = s3Client.getBucket("project").asFile();
        final S3File actual = this.project.src().get();
        assertEquals(expected, actual.getParentFile());
    }

    @Test
    public void s3Files() throws Exception {
        final List<String> list = this.project.src().files()
                .map(S3File::getAbsoluteName)
                .sorted()
                .collect(Collectors.toList());

        assertEquals("" +
                "src/main/java/org/supertribe/colors/Green.java\n" +
                "src/main/java/org/supertribe/colors/Red.java\n" +
                "src/main/resources/square.txt\n" +
                "src/main/resources/triangle.txt\n" +
                "src/test/java/org/supertribe/colors/GreenTest.java\n" +
                "src/test/java/org/supertribe/colors/RedTest.java\n" +
                "src/test/resources/circle.txt\n" +
                "src/test/resources/rectangle.txt", Join.join("\n", list));
    }

    @Test
    public void s3File() throws Exception {
        final S3File file = project.file("pom.xml");
        assertEquals(file, project.pomXml());
    }

    @Test
    public void returnInterface() throws Exception {
        final Src src = project.src();
        assertNotNull(src);
        assertEquals("src", src.get().getAbsoluteName());
    }

    @Test
    public void returnInterfaceWithArg() throws Exception {
        final Section main = project.src().section("main");
        assertNotNull(main);
        assertEquals("src/main/java", main.java().getAbsoluteName());
    }

    @Test
    public void returnS3File() throws Exception {
        final S3File java = project.src().section("main").java();
        assertEquals("src/main/java", java.getAbsoluteName());
    }

    @Test
    public void returnArrayOfInterface() throws Exception {
        final Returns returns = S3.of(Returns.class, project.src().main().java());

        final Java[] data = returns.returnArrayOfInterface();
        assertEquals("" +
                "src/main/java/org/supertribe/colors/Green.java\n" +
                "src/main/java/org/supertribe/colors/Red.java", Join.join("\n", (Object[]) data));
    }

    @Test
    public void returnArrayOfS3File() throws Exception {
        final Returns returns = S3.of(Returns.class, project.src().main().java());

        final S3File[] data = returns.returnArrayOfS3File();
        assertEquals("" +
                "src/main/java/org/supertribe/colors/Green.java\n" +
                "src/main/java/org/supertribe/colors/Red.java", Join.join("\n", S3File::getAbsoluteName, data));
    }

    @Test
    public void returnStreamOfInterface() throws Exception {
        final Returns returns = S3.of(Returns.class, project.src().main().java());

        final List<Java> data = returns.returnStreamOfInterface().collect(Collectors.toList());
        assertEquals("" +
                "src/main/java/org/supertribe/colors/Green.java\n" +
                "src/main/java/org/supertribe/colors/Red.java", Join.join("\n", data));
    }

    @Test
    public void returnStreamOfS3File() throws Exception {
        final Returns returns = S3.of(Returns.class, project.src().main().java());

        final List<S3File> data = returns.returnStreamOfS3File().collect(Collectors.toList());
        assertEquals("" +
                "src/main/java/org/supertribe/colors/Green.java\n" +
                "src/main/java/org/supertribe/colors/Red.java", Join.join("\n", S3File::getAbsoluteName, data));
    }

    @Test
    public void returnListOfInterface() throws Exception {
        final Returns returns = S3.of(Returns.class, project.src().main().java());

        final List<Java> data = returns.returnListOfInterface();
        assertEquals("" +
                "src/main/java/org/supertribe/colors/Green.java\n" +
                "src/main/java/org/supertribe/colors/Red.java", Join.join("\n", data));
    }

    @Test
    public void returnListOfS3File() throws Exception {
        final Returns returns = S3.of(Returns.class, project.src().main().java());

        final List<S3File> data = returns.returnListOfS3File();
        assertEquals("" +
                "src/main/java/org/supertribe/colors/Green.java\n" +
                "src/main/java/org/supertribe/colors/Red.java", Join.join("\n", S3File::getAbsoluteName, data));
    }

    @Test
    public void returnSetOfInterface() throws Exception {
        final Returns returns = S3.of(Returns.class, project.src().main().java());

        final Set<Java> data = returns.returnSetOfInterface();
        assertEquals("" +
                "src/main/java/org/supertribe/colors/Green.java\n" +
                "src/main/java/org/supertribe/colors/Red.java", Join.join("\n", data));
    }

    @Test
    public void returnSetOfS3File() throws Exception {
        final Returns returns = S3.of(Returns.class, project.src().main().java());

        final Set<S3File> data = returns.returnSetOfS3File();
        assertEquals("" +
                "src/main/java/org/supertribe/colors/Green.java\n" +
                "src/main/java/org/supertribe/colors/Red.java", Join.join("\n", S3File::getAbsoluteName, data));
    }

    @Test
    public void returnCollectionOfInterface() throws Exception {
        final Returns returns = S3.of(Returns.class, project.src().main().java());

        final Collection<Java> data = returns.returnCollectionOfInterface();
        assertEquals("" +
                "src/main/java/org/supertribe/colors/Green.java\n" +
                "src/main/java/org/supertribe/colors/Red.java", Join.join("\n", data));
    }

    @Test
    public void returnCollectionOfS3File() throws Exception {
        final Returns returns = S3.of(Returns.class, project.src().main().java());

        final Collection<S3File> data = returns.returnCollectionOfS3File();
        assertEquals("" +
                "src/main/java/org/supertribe/colors/Green.java\n" +
                "src/main/java/org/supertribe/colors/Red.java", Join.join("\n", S3File::getAbsoluteName, data));
    }

    @Test
    public void returnFilteredArrayOfInterface() throws Exception {
        final FilteredReturns returns = S3.of(FilteredReturns.class, project.src().main().java());

        final Java[] data = returns.returnArrayOfInterface();
        assertEquals("" +
                "src/main/java/org/supertribe/colors/Green.java", Join.join("\n", (Object[]) data));
    }

    @Test
    public void returnFilteredArrayOfS3File() throws Exception {
        final FilteredReturns returns = S3.of(FilteredReturns.class, project.src().main().java());

        final S3File[] data = returns.returnArrayOfS3File();
        assertEquals("" +
                "src/main/java/org/supertribe/colors/Green.java", Join.join("\n", S3File::getAbsoluteName, data));
    }

    @Test
    public void returnFilteredStreamOfInterface() throws Exception {
        final FilteredReturns returns = S3.of(FilteredReturns.class, project.src().main().java());

        final List<Java> data = returns.returnStreamOfInterface().collect(Collectors.toList());
        assertEquals("" +
                "src/main/java/org/supertribe/colors/Green.java", Join.join("\n", data));
    }

    @Test
    public void returnFilteredStreamOfS3File() throws Exception {
        final FilteredReturns returns = S3.of(FilteredReturns.class, project.src().main().java());

        final List<S3File> data = returns.returnStreamOfS3File().collect(Collectors.toList());
        assertEquals("" +
                "src/main/java/org/supertribe/colors/Green.java", Join.join("\n", S3File::getAbsoluteName, data));
    }

    @Test
    public void returnFilteredListOfInterface() throws Exception {
        final FilteredReturns returns = S3.of(FilteredReturns.class, project.src().main().java());

        final List<Java> data = returns.returnListOfInterface();
        assertEquals("" +
                "src/main/java/org/supertribe/colors/Green.java", Join.join("\n", data));
    }

    @Test
    public void returnFilteredListOfS3File() throws Exception {
        final FilteredReturns returns = S3.of(FilteredReturns.class, project.src().main().java());

        final List<S3File> data = returns.returnListOfS3File();
        assertEquals("" +
                "src/main/java/org/supertribe/colors/Green.java", Join.join("\n", S3File::getAbsoluteName, data));
    }

    @Test
    public void returnFilteredSetOfInterface() throws Exception {
        final FilteredReturns returns = S3.of(FilteredReturns.class, project.src().main().java());

        final Set<Java> data = returns.returnSetOfInterface();
        assertEquals("" +
                "src/main/java/org/supertribe/colors/Green.java", Join.join("\n", data));
    }

    @Test
    public void returnFilteredSetOfS3File() throws Exception {
        final FilteredReturns returns = S3.of(FilteredReturns.class, project.src().main().java());

        final Set<S3File> data = returns.returnSetOfS3File();
        assertEquals("" +
                "src/main/java/org/supertribe/colors/Green.java", Join.join("\n", S3File::getAbsoluteName, data));
    }

    @Test
    public void returnFilteredCollectionOfInterface() throws Exception {
        final FilteredReturns returns = S3.of(FilteredReturns.class, project.src().main().java());

        final Collection<Java> data = returns.returnCollectionOfInterface();
        assertEquals("" +
                "src/main/java/org/supertribe/colors/Green.java", Join.join("\n", data));
    }

    @Test
    public void returnFilteredCollectionOfS3File() throws Exception {
        final FilteredReturns returns = S3.of(FilteredReturns.class, project.src().main().java());

        final Collection<S3File> data = returns.returnCollectionOfS3File();
        assertEquals("" +
                "src/main/java/org/supertribe/colors/Green.java", Join.join("\n", S3File::getAbsoluteName, data));
    }

    @Test
    public void filtersAnnotation() throws Exception {

        final MultipleFilteredReturns multipleFilteredReturns = S3.of(MultipleFilteredReturns.class, project.get());

        final S3File[] data = multipleFilteredReturns.multipleFilters();
        assertEquals("" +
                "src/main/resources/square.txt\n" +
                "src/main/resources/triangle.txt\n" +
                "src/test/resources/circle.txt\n" +
                "src/test/resources/rectangle.txt", Join.join("\n", S3File::getAbsoluteName, data));
    }

    @Test
    public void nameAnnotation() throws Exception {
        final S3File s3File = project.pomXml();
        assertEquals("pom.xml", s3File.getAbsoluteName());
    }

    @Test
    public void defaultMethod() throws Exception {
        final DefaultMethods defaultMethods = S3.of(DefaultMethods.class, project.get());

        final List<S3File> data = defaultMethods.getTextFiles().collect(Collectors.toList());
        assertEquals("" +
                "src/main/resources/square.txt\n" +
                "src/main/resources/triangle.txt\n" +
                "src/test/resources/circle.txt\n" +
                "src/test/resources/rectangle.txt\n" +
                "target/bar.txt\n" +
                "target/foo.txt", Join.join("\n", S3File::getAbsoluteName, data));
    }

    @Test
    public void defaultMethodWithArgs() throws Exception {
        final DefaultMethods defaultMethods = S3.of(DefaultMethods.class, project.get());

        final List<S3File> data = defaultMethods.getFilesWithExtension("txt").collect(Collectors.toList());
        assertEquals("" +
                "src/main/resources/square.txt\n" +
                "src/main/resources/triangle.txt\n" +
                "src/test/resources/circle.txt\n" +
                "src/test/resources/rectangle.txt\n" +
                "target/bar.txt\n" +
                "target/foo.txt", Join.join("\n", S3File::getAbsoluteName, data));
    }

    @Test
    public void prefixAnnotation() throws Exception {
        final RequestAnnotations dir = S3.of(RequestAnnotations.class, project.src().main().java().getParentFile());

        final List<S3File> data = dir.javaFiles().collect(Collectors.toList());
        assertEquals("" +
                "src/main/java/org/supertribe/colors/Green.java\n" +
                "src/main/java/org/supertribe/colors/Red.java", Join.join("\n", S3File::getAbsoluteName, data));
    }

    @Test
    public void markerAnnotation() throws Exception {
        final RequestAnnotations dir = S3.of(RequestAnnotations.class, project.get());

        final List<S3File> data = dir.afterRedTest().collect(Collectors.toList());
        assertEquals("" +
                "src/test/resources/circle.txt\n" +
                "src/test/resources/rectangle.txt\n" +
                "target/bar.txt\n" +
                "target/foo.txt", Join.join("\n", S3File::getAbsoluteName, data));
    }

    @Test
    public void delimiterAnnotation() throws Exception {
        final RequestAnnotations dir = S3.of(RequestAnnotations.class, this.project.get());

        final List<S3File> data = dir.slash().collect(Collectors.toList());
        assertEquals("pom.xml", Join.join("\n", S3File::getAbsoluteName, data));
    }

    public interface Project extends S3 {
        Src src();

        S3File target();

        @Name("pom.xml")
        S3File pomXml();
    }

    public interface Src extends S3 {
        Section main();

        Section test();

        Section section(final String name);
    }

    public interface Section {
        S3File java();

        S3File resources();
    }

    public interface Java extends S3 {
    }

    public interface Returns {
        Java[] returnArrayOfInterface();

        S3File[] returnArrayOfS3File();

        Stream<Java> returnStreamOfInterface();

        Stream<S3File> returnStreamOfS3File();

        List<Java> returnListOfInterface();

        List<S3File> returnListOfS3File();

        Set<Java> returnSetOfInterface();

        Set<S3File> returnSetOfS3File();

        Collection<Java> returnCollectionOfInterface();

        Collection<S3File> returnCollectionOfS3File();
    }

    public interface MultipleFilteredReturns {
        @Filter(IsTxt.class)
        @Filter(IsSrc.class)
        S3File[] multipleFilters();
    }

    public interface FilteredReturns {
        @Filter(IsGreen.class)
        Java[] returnArrayOfInterface();

        @Filter(IsGreen.class)
        S3File[] returnArrayOfS3File();

        @Filter(IsGreen.class)
        Stream<Java> returnStreamOfInterface();

        @Filter(IsGreen.class)
        Stream<S3File> returnStreamOfS3File();

        @Filter(IsGreen.class)
        List<Java> returnListOfInterface();

        @Filter(IsGreen.class)
        List<S3File> returnListOfS3File();

        @Filter(IsGreen.class)
        Set<Java> returnSetOfInterface();

        @Filter(IsGreen.class)
        Set<S3File> returnSetOfS3File();

        @Filter(IsGreen.class)
        Collection<Java> returnCollectionOfInterface();

        @Filter(IsGreen.class)
        Collection<S3File> returnCollectionOfS3File();
    }

    public static class IsGreen implements Predicate<S3File> {
        @Override
        public boolean test(final S3File s3File) {
            return s3File.getAbsoluteName().toLowerCase().contains("green");
        }
    }

    public static class IsTxt implements Predicate<S3File> {
        @Override
        public boolean test(final S3File s3File) {
            return s3File.getName().endsWith(".txt");
        }
    }

    public static class IsSrc implements Predicate<S3File> {
        @Override
        public boolean test(final S3File s3File) {
            return s3File.getAbsoluteName().toLowerCase().startsWith("src/");
        }
    }

    public interface DefaultMethods extends S3 {

        default Stream<S3File> getTextFiles() {
            return files().filter(s3File -> s3File.getName().endsWith(".txt"));
        }

        default Stream<S3File> getFilesWithExtension(final String ext) {
            return files().filter(s3File -> s3File.getName().endsWith("." + ext));
        }
    }

    public interface RequestAnnotations extends S3 {
        @Prefix("java")
        Stream<S3File> javaFiles();

        @Marker("src/test/java/org/supertribe/colors/RedTest.java")
        Stream<S3File> afterRedTest();

        @Delimiter("/")
        Stream<S3File> slash();
    }
}
