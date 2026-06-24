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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class S3AssertsTest {


    @RegisterExtension
    public MockS3Extension mockS3 = new MockS3Extension();
    private S3Client s3Client;

    @Test
    public final void test() throws Exception {
        this.s3Client = new S3Client(mockS3.getS3Client());

        s3Client.createBucket("colors")
                .put("red/light.txt", "rose")
                .put("red/dark.txt", "crimson")
                .put("green/light.txt", "lime")
                .put("green/dark.txt", "forest")
                .put("blue/light.txt", "sky")
                .put("blue/dark.txt", "navy")
                .put("readme.txt", "about colors");

        final S3Asserts asserts = S3Asserts.of(s3Client.getS3(), "colors");

        asserts.list()
                .etag()
                .size()
                .lastModified()
                .key()
                .replaceAll("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z","2000-00-00T00:00:00Z")
                .assertListing("" +
                        "f3311d3d14c8437553bf3e3f90920736            4 2000-00-00T00:00:00Z blue/dark.txt\n" +
                        "900bc885d7553375aec470198a9514f3            3 2000-00-00T00:00:00Z blue/light.txt\n" +
                        "f379cfd7a55b621577a8389d1817a102            6 2000-00-00T00:00:00Z green/dark.txt\n" +
                        "67c0ecaf5a1b782b11146e9fbe80f016            4 2000-00-00T00:00:00Z green/light.txt\n" +
                        "1398f181882242cbe381ef3e7c13e32e           12 2000-00-00T00:00:00Z readme.txt\n" +
                        "5fa3ccc64c973db27e9fcade0810423e            7 2000-00-00T00:00:00Z red/dark.txt\n" +
                        "fcdc7b4207660a1372d0cd5491ad856e            4 2000-00-00T00:00:00Z red/light.txt");

        asserts.list()
                .etag()
                .lastModified()
                .size(3)
                .key()
                .replaceAll("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z","2000-00-00T00:00:00Z")
                .assertListing("" +
                        "f3311d3d14c8437553bf3e3f90920736 2000-00-00T00:00:00Z   4 blue/dark.txt\n" +
                        "900bc885d7553375aec470198a9514f3 2000-00-00T00:00:00Z   3 blue/light.txt\n" +
                        "f379cfd7a55b621577a8389d1817a102 2000-00-00T00:00:00Z   6 green/dark.txt\n" +
                        "67c0ecaf5a1b782b11146e9fbe80f016 2000-00-00T00:00:00Z   4 green/light.txt\n" +
                        "1398f181882242cbe381ef3e7c13e32e 2000-00-00T00:00:00Z  12 readme.txt\n" +
                        "5fa3ccc64c973db27e9fcade0810423e 2000-00-00T00:00:00Z   7 red/dark.txt\n" +
                        "fcdc7b4207660a1372d0cd5491ad856e 2000-00-00T00:00:00Z   4 red/light.txt");
    }


}
