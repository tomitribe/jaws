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
import org.tomitribe.util.IO;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class FooTest {

    @Rule
    public MockS3 mockS3 = new MockS3();
    private File store;
    private org.tomitribe.jaws.s3.S3Client s3Client;


    @Before
    public final void setUp() throws Exception {
        this.store = mockS3.getBlobStoreLocation();
    }

    private static final String BUCKET_NAME = "repository";
    private static final String OBJECT_KEY = "example-object.txt";

    @Test
    public void foo() throws IOException {
        new Archive()
                .add("repository/org.color/red/1/1.4/foo.txt", "")
                .add("repository/org.color.bright/green/1/1.4/foo.txt", "")
                .add("repository/junit/junit/4/4.12/bar.txt", "")
                .add("repository/io.tomitribe/crest/5/5.4.1.2/baz.txt", "")
                .toDir(store);
//        System.setProperty("software.amazon.awssdk.request.log.level", "debug");
        final S3Client s3 = mockS3.getS3Client();
        uploadFile(s3);
        listObjects(s3);
        downloadFile(s3);

        s3.close();
    }

    private static void uploadFile(final S3Client s3) {
        final String content = "Hello from AWS SDK v2!";
        final PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(OBJECT_KEY)
                .build();

        s3.putObject(putRequest, RequestBody.fromString(content));
        System.out.println("✅ Uploaded: " + OBJECT_KEY);
    }

    private static void listObjects(final S3Client s3) {
        final ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(BUCKET_NAME)
                .build();

        final ListObjectsV2Response listResponse = s3.listObjectsV2(listRequest);
        final List<S3Object> objects = listResponse.contents();

        System.out.println("📦 Objects in bucket:");
        for (final S3Object obj : objects) {
            System.out.println("- " + obj.key() + " (" + obj.size() + " bytes)");
        }
    }

    private static void downloadFile(final S3Client s3) throws IOException {
        final GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(OBJECT_KEY)
                .build();

        try (final ResponseInputStream<GetObjectResponse> s3Object = s3.getObject(getRequest)) {
            final byte[] bytes = IO.readBytes(s3Object);
            final String content = new String(bytes, StandardCharsets.UTF_8);
            System.out.println("📥 Downloaded content:\n" + content);
        }
    }
}
