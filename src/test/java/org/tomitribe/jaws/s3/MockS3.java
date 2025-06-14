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

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.gaul.s3proxy.junit.S3ProxyJunitCore;
import org.gaul.s3proxy.junit.S3ProxyRule;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URI;

public class MockS3 extends ExternalResource {

    private final S3ProxyRule s3Proxy = S3ProxyRule
            .builder()
            .withCredentials("access", "secret")
            .build();

    public static AmazonS3 getAmazonS3(final S3ProxyRule s3Proxy) {
        return AmazonS3ClientBuilder
                .standard()
                .withCredentials(
                        new AWSStaticCredentialsProvider(
                                new BasicAWSCredentials(
                                        s3Proxy.getAccessKey(), s3Proxy.getSecretKey())))
                .withEndpointConfiguration(
                        new AwsClientBuilder.EndpointConfiguration(
                                s3Proxy.getUri().toString(), Regions.US_EAST_1.getName()))
                .build();
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        return s3Proxy.apply(base, description);
    }

    public AmazonS3 getAmazonS3() {
        return getAmazonS3(s3Proxy);
    }

    public S3Client getS3Client() {
        final AwsBasicCredentials awsCreds = AwsBasicCredentials.create("access", "secret");

        final URI uri = s3Proxy.getUri();
        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .endpointOverride(uri)
                .region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    public File getBlobStoreLocation() {
        final Object core = get(S3ProxyRule.class, s3Proxy, "core");
        final Object blobStoreLocation = get(S3ProxyJunitCore.class, core, "blobStoreLocation");
        return blobStoreLocation instanceof File ? (File) blobStoreLocation : null;
    }

    private Object get(final Class<?> clazz, final Object object, final String field) {
        try {
            final Field blobStoreLocation = clazz.getDeclaredField(field);
            blobStoreLocation.setAccessible(true);
            return blobStoreLocation.get(object);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to get " + field, e);
        }
    }
}
