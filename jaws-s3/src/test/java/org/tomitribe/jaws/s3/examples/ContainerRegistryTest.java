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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.tomitribe.jaws.s3.MockS3Rule;
import org.tomitribe.jaws.s3.Name;
import org.tomitribe.jaws.s3.S3;
import org.tomitribe.jaws.s3.S3Client;
import org.tomitribe.util.Archive;
import org.tomitribe.util.Join;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests modeled after the Container Registry example in docs/examples/container-registry.md.
 *
 * Verifies deep interface chaining, @Name for _prefixed keys, default methods
 * on S3.File, and @Walk with S3.File dispatch.
 */
public class ContainerRegistryTest {

    @Rule
    public MockS3Rule mockS3 = new MockS3Rule();
    private S3Client s3Client;

    @Before
    public final void setUp() throws Exception {
        final File store = mockS3.getBlobStoreLocation();
        this.s3Client = new S3Client(mockS3.getS3Client());

        new Archive()
                // api-server image
                .add("registry/repositories/myorg/api-server/_manifests/tags/latest/current/link", "sha256:abc123")
                .add("registry/repositories/myorg/api-server/_manifests/tags/v1.0.0/current/link", "sha256:abc123")
                .add("registry/repositories/myorg/api-server/_manifests/tags/v1.1.0/current/link", "sha256:def456")
                .add("registry/repositories/myorg/api-server/_manifests/revisions/sha256/abc123/link", "sha256:abc123")
                .add("registry/repositories/myorg/api-server/_layers/sha256/layer01/link", "sha256:layer01")
                .add("registry/repositories/myorg/api-server/_layers/sha256/layer02/link", "sha256:layer02")
                // web-frontend image
                .add("registry/repositories/myorg/web-frontend/_manifests/tags/latest/current/link", "sha256:fff999")
                .add("registry/repositories/myorg/web-frontend/_layers/sha256/layeraa/link", "sha256:layeraa")
                // blobs
                .add("registry/blobs/sha256/abc123/data", "manifest-data")
                .add("registry/blobs/sha256/def456/data", "manifest-data-2")
                .add("registry/blobs/sha256/layer01/data", "layer-data-1")
                .add("registry/blobs/sha256/layer02/data", "layer-data-2")
                .toDir(store);
    }

    private Registry registry() {
        return s3Client.getBucket("registry").as(Registry.class);
    }

    /**
     * Deep chain: repositories → namespace → image → _manifests → tags → tag → current → link
     */
    @Test
    public void resolveTagToLink() throws Exception {
        final LinkFile link = registry().repositories()
                .namespace("myorg")
                .image("api-server")
                .manifests()
                .tags()
                .tag("v1.0.0")
                .current()
                .link();

        Assert.assertEquals("sha256:abc123", link.getValueAsString());
    }

    /**
     * Default method getAlgorithm() parses the algorithm from "sha256:abc123".
     */
    @Test
    public void linkGetAlgorithm() throws Exception {
        final LinkFile link = registry().repositories()
                .namespace("myorg")
                .image("api-server")
                .manifests()
                .tags()
                .tag("v1.0.0")
                .current()
                .link();

        assertEquals("sha256", link.getAlgorithm());
    }

    /**
     * Default method getDigest() parses the hash from "sha256:abc123".
     */
    @Test
    public void linkGetDigest() throws Exception {
        final LinkFile link = registry().repositories()
                .namespace("myorg")
                .image("api-server")
                .manifests()
                .tags()
                .tag("v1.0.0")
                .current()
                .link();

        assertEquals("abc123", link.getDigest());
    }

    /**
     * Stream<Tag> tags() should list all tag directories for an image.
     */
    @Test
    public void listTags() throws Exception {
        final ImageRepo apiServer = registry().repositories()
                .namespace("myorg")
                .image("api-server");

        final List<String> tags = apiServer.manifests().tags().tags()
                .map(t -> t.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("latest\nv1.0.0\nv1.1.0", Join.join("\n", tags));
    }

    /**
     * Stream<ImageRepo> images() should list image repositories in a namespace.
     */
    @Test
    public void listImages() throws Exception {
        final List<String> images = registry().repositories()
                .namespace("myorg")
                .images()
                .map(img -> img.file().getName())
                .sorted()
                .collect(Collectors.toList());

        assertEquals("api-server\nweb-frontend", Join.join("\n", images));
    }

    /**
     * @Name("_manifests") and @Name("_layers") should resolve underscore-prefixed keys.
     */
    @Test
    public void nameAnnotationUnderscoreKeys() throws Exception {
        final ImageRepo apiServer = registry().repositories()
                .namespace("myorg")
                .image("api-server");

        Assert.assertEquals("_manifests", apiServer.manifests().file().getName());
        Assert.assertEquals("_layers", apiServer.layers().file().getName());
    }

    /**
     * getValueAsStream() should return the binary content of a blob.
     */
    @Test
    public void blobGetValueAsStream() throws Exception {
        final BlobData blob = registry().blobs()
                .sha256()
                .digest("abc123")
                .data();

        try (final InputStream is = blob.getValueAsStream()) {
            assertNotNull(is);
            final byte[] bytes = new byte[1024];
            final int len = is.read(bytes);
            assertEquals("manifest-data", new String(bytes, 0, len));
        }
    }

    /**
     * DigestEntry link content is readable through the chain.
     */
    @Test
    public void layerLinkContent() throws Exception {
        final LinkFile link = registry().repositories()
                .namespace("myorg")
                .image("api-server")
                .layers()
                .sha256()
                .digest("layer01")
                .link();

        Assert.assertEquals("sha256:layer01", link.getValueAsString());
    }

    // ---------------------------------------------------------------
    // Test interfaces — modeled after docs/examples/container-registry.md
    // ---------------------------------------------------------------

    public interface Registry extends S3.Dir {
        Repositories repositories();
        BlobStore blobs();
    }

    public interface Repositories extends S3.Dir {
        Namespace namespace(String name);
        Stream<Namespace> namespaces();
    }

    public interface Namespace extends S3.Dir {
        ImageRepo image(String name);
        Stream<ImageRepo> images();
    }

    public interface ImageRepo extends S3.Dir {
        @Name("_manifests")
        Manifests manifests();

        @Name("_layers")
        Layers layers();
    }

    public interface Manifests extends S3.Dir {
        Tags tags();
        Revisions revisions();
    }

    public interface Tags extends S3.Dir {
        Tag tag(String name);
        Stream<Tag> tags();
    }

    public interface Tag extends S3.Dir {
        Current current();
    }

    public interface Current extends S3.Dir {
        LinkFile link();
    }

    public interface Revisions extends S3.Dir {
        DigestDir sha256();
    }

    public interface Layers extends S3.Dir {
        DigestDir sha256();
    }

    public interface DigestDir extends S3.Dir {
        DigestEntry digest(String hash);
        Stream<DigestEntry> digests();
    }

    public interface DigestEntry extends S3.Dir {
        LinkFile link();
        BlobData data();
    }

    public interface LinkFile extends S3.File {
        default String getAlgorithm() {
            final String content = getValueAsString().trim();
            return content.substring(0, content.indexOf(':'));
        }

        default String getDigest() {
            final String content = getValueAsString().trim();
            return content.substring(content.indexOf(':') + 1);
        }
    }

    public interface BlobStore extends S3.Dir {
        DigestDir sha256();
    }

    public interface BlobData extends S3.File {
    }
}
