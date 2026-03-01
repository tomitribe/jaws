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

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.io.File;

public class MockS3Extension implements BeforeEachCallback, AfterEachCallback {

    private final MockS3 mockS3 = new MockS3();

    @Override
    public void beforeEach(final ExtensionContext context) throws Exception {
        mockS3.start();
    }

    @Override
    public void afterEach(final ExtensionContext context) {
        mockS3.stop();
    }

    public software.amazon.awssdk.services.s3.S3Client getS3Client() {
        return mockS3.getS3Client();
    }

    public S3AsyncClient getS3AsyncClient() {
        return mockS3.getS3AsyncClient();
    }

    public File getBlobStoreLocation() {
        return mockS3.getBlobStoreLocation();
    }
}
