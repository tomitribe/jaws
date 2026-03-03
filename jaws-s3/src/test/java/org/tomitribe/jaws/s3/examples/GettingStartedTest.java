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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.tomitribe.jaws.s3.Match;
import org.tomitribe.jaws.s3.MockS3Extension;
import org.tomitribe.jaws.s3.Name;
import org.tomitribe.jaws.s3.S3;
import org.tomitribe.jaws.s3.S3Asserts;
import org.tomitribe.jaws.s3.S3Client;
import org.tomitribe.jaws.s3.S3File;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A minimal JUnit 5 example showing how to set up a mock S3,
 * populate a bucket, use typed proxies, and verify with S3Asserts.
 *
 * Copy this test as a starting template for your own tests.
 */
public class GettingStartedTest {

    @RegisterExtension
    private final MockS3Extension mockS3 = new MockS3Extension();

    @Test
    void test() {
        final S3Client s3Client = new S3Client(mockS3.getS3Client());

        final UserRepository root = s3Client.createBucket("my-bucket")
                .put("config.properties", "color=red")
                .put("users/alice.json", "{\"name\":\"Alice\"}")
                .put("users/bob.json", "{\"name\":\"Bob\"}")
                .as(UserRepository.class);

        // Read entries
        assertEquals("color=red", root.config().getValueAsString());
        assertEquals("{\"name\":\"Alice\"}", root.users().user("alice.json").getValueAsString());

        // List entries
        final String names = root.users().users()
                .map(UserFile::getUserName)
                .sorted()
                .collect(Collectors.joining(", "));

        assertEquals("alice, bob", names);

        // Add entry
        root.users().user("charlie.json").setValueAsString("{\"name\":\"Charlie\"}");

        // Review all results
        S3Asserts.of(mockS3.getS3Client(), "my-bucket")
                .snapshot()
                .assertContent("users/charlie.json", "{\"name\":\"Charlie\"}")
                .assertContent("users/alice.json", "{\"name\":\"Alice\"}")
                .assertExists("config.properties")
                .assertNotExists("users/dave.json");
    }

    public interface UserRepository extends S3.Dir {
        @Name("config.properties")
        S3File config();

        Users users();
    }

    public interface Users extends S3.Dir {
        Stream<UserFile> users();

        UserFile user(String name);
    }

    @Match(".*\\.json")
    public interface UserFile extends S3.File {
        default String getUserName() {
            return file().getName().replaceAll("\\.json$", "");
        }
    }
}
