/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tomitribe.jaws.s3;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.tomitribe.util.Archive;
import org.tomitribe.util.dir.Name;
import org.tomitribe.util.dir.Parent;

import java.io.File;

import static junit.framework.Assert.fail;
import static junit.framework.TestCase.assertEquals;

public class S3ParentTest {


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
    public void test() throws Exception {
        Archive.archive()
                .add("orange/src/main/java/org/example/Foo.java", "")
                .add("orange/src/main/java/four.txt", "")
                .add("orange/src/main/three.txt", "")
                .add("orange/src/two.txt", "")
                .add("orange/one.txt", "")
                .add("orange/pom.xml", "")
                .toDir(store);

        final S3Bucket bucket = s3Client.getBucket("orange");
        final Module module = S3.of(Module.class, bucket.asFile());

        final Src src = module.src();
        final Module parent = src.module();

        assertEquals(module.get().getPath().getAbsoluteName(), parent.get().getPath().getAbsoluteName());
    }

    @Test
    public void depth() throws Exception {
        Archive.archive()
                .add("orange/src/main/java/org/example/Foo.java", "")
                .add("orange/src/main/java/four.txt", "")
                .add("orange/src/main/three.txt", "")
                .add("orange/src/two.txt", "")
                .add("orange/one.txt", "")
                .add("orange/pom.xml", "")
                .toDir(store);

        final S3Bucket bucket = s3Client.getBucket("orange");
        final Module module = S3.of(Module.class, bucket.asFile());

        final Src src = module.src();
        final Section main = src.main();
        final Java java = main.java();

        final String expected = module.get().getPath().getAbsoluteName();

        assertEquals(expected, src.module().get().getPath().getAbsoluteName());
        assertEquals(expected, main.module().get().getPath().getAbsoluteName());
        assertEquals(expected, java.module().get().getPath().getAbsoluteName());
    }

    @Test
    public void noParent() throws Exception {
        Archive.archive()
                .add("orange/src/main/java/org/example/Foo.java", "")
                .add("orange/src/main/java/four.txt", "")
                .add("orange/src/main/three.txt", "")
                .add("orange/src/two.txt", "")
                .add("orange/one.txt", "")
                .add("orange/pom.xml", "")
                .toDir(store);

        final S3Bucket bucket = s3Client.getBucket("orange");
        final Module module = S3.of(Module.class, bucket.asFile());

        try {
            module.module();
            fail("Expected NoParentException");
        } catch (final NoParentException e) {
            // pass
        }
    }


    public interface Module extends S3 {

        @Parent
        Module module();

        @Name("pom.xml")
        File pomXml();

        Src src();
    }

    public interface Src extends S3 {

        @Parent
        Module module();

        Section main();

        Section test();
    }

    public interface Section extends S3 {
        @Parent(2)
        Module module();

        Java java();

        File resources();
    }

    public interface Java extends S3 {

        @Parent(3)
        Module module();
    }

}
