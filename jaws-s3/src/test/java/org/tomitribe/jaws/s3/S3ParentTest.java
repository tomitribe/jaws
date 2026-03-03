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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class S3ParentTest {


    @RegisterExtension
    public MockS3Extension mockS3 = new MockS3Extension();
    private S3Client s3Client;

    @BeforeEach
    public final void setUp() throws Exception {
        this.s3Client = new S3Client(mockS3.getS3Client());
    }


    @Test
    public void test() throws Exception {
        final S3Bucket bucket = s3Client.createBucket("orange")
                .put("src/main/java/org/example/Foo.java", "")
                .put("src/main/java/four.txt", "")
                .put("src/main/three.txt", "")
                .put("src/two.txt", "")
                .put("one.txt", "")
                .put("pom.xml", "");

        final Module module = bucket.as(Module.class);

        final Src src = module.src();
        final Module parent = src.module();

        assertEquals(module.file().getPath().getAbsoluteName(), parent.file().getPath().getAbsoluteName());
    }

    @Test
    public void depth() throws Exception {
        final S3Bucket bucket = s3Client.createBucket("orange")
                .put("src/main/java/org/example/Foo.java", "")
                .put("src/main/java/four.txt", "")
                .put("src/main/three.txt", "")
                .put("src/two.txt", "")
                .put("one.txt", "")
                .put("pom.xml", "");

        final Module module = bucket.as(Module.class);

        final Src src = module.src();
        final Section main = src.main();
        final Java java = main.java();

        final String expected = module.file().getPath().getAbsoluteName();

        assertEquals(expected, src.module().file().getPath().getAbsoluteName());
        assertEquals(expected, main.module().file().getPath().getAbsoluteName());
        assertEquals(expected, java.module().file().getPath().getAbsoluteName());
    }

    @Test
    public void noParent() throws Exception {
        final S3Bucket bucket = s3Client.createBucket("orange")
                .put("src/main/java/org/example/Foo.java", "")
                .put("src/main/java/four.txt", "")
                .put("src/main/three.txt", "")
                .put("src/two.txt", "")
                .put("one.txt", "")
                .put("pom.xml", "");

        final Module module = bucket.as(Module.class);

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
