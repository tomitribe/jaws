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

import org.junit.jupiter.api.Test;
import org.tomitribe.util.IO;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ObjectMetadataContentTypeTest {

    private static String contentType(final String fileName) {
        return ObjectMetadata.contentType(fileName, "application/octet-stream");
    }

    // ---------------------------------------------------------------
    // Explicit mappings
    // ---------------------------------------------------------------

    @Test
    public void tarGz() {
        assertEquals("application/x-gzip", contentType("archive.tar.gz"));
    }

    @Test
    public void jar() {
        assertEquals("application/java-archive", contentType("lib.jar"));
    }

    @Test
    public void war() {
        assertEquals("application/java-archive", contentType("app.war"));
    }

    @Test
    public void ear() {
        assertEquals("application/java-archive", contentType("app.ear"));
    }

    @Test
    public void rar() {
        assertEquals("application/java-archive", contentType("connector.rar"));
    }

    @Test
    public void pom() {
        assertEquals("application/xml", contentType("library.pom"));
    }

    @Test
    public void md5() {
        assertEquals("text/plain", contentType("checksum.md5"));
    }

    @Test
    public void sha1() {
        assertEquals("text/plain", contentType("checksum.sha1"));
    }

    @Test
    public void sha256() {
        assertEquals("text/plain", contentType("checksum.sha256"));
    }

    @Test
    public void sha512() {
        assertEquals("text/plain", contentType("checksum.sha512"));
    }

    @Test
    public void asc() {
        assertEquals("text/plain", contentType("signature.asc"));
    }

    // ---------------------------------------------------------------
    // JDK-detected types
    // ---------------------------------------------------------------

    @Test
    public void html() {
        assertEquals("text/html", contentType("index.html"));
    }

    @Test
    public void txt() {
        assertEquals("text/plain", contentType("readme.txt"));
    }

    @Test
    public void xml() {
        assertEquals("application/xml", contentType("config.xml"));
    }

    @Test
    public void png() {
        assertEquals("image/png", contentType("logo.png"));
    }

    @Test
    public void jpg() {
        assertEquals("image/jpeg", contentType("photo.jpg"));
    }

    @Test
    public void gif() {
        assertEquals("image/gif", contentType("icon.gif"));
    }

    // ---------------------------------------------------------------
    // Default fallback
    // ---------------------------------------------------------------

    @Test
    public void unknownExtension() {
        assertEquals("application/octet-stream", contentType("data.xyz123"));
    }

    @Test
    public void noExtension() {
        assertEquals("application/octet-stream", contentType("Makefile"));
    }

    // ---------------------------------------------------------------
    // ObjectMetadata.from(File)
    // ---------------------------------------------------------------

    @Test
    public void fromFile() throws IOException {
        final File file = File.createTempFile("test", ".jar");
        file.deleteOnExit();
        IO.copy(IO.read("hello world"), file);

        final ObjectMetadata metadata = ObjectMetadata.from(file);

        assertEquals(11, metadata.getContentLength());
        assertEquals("application/java-archive", metadata.getContentType());
        assertNotNull(metadata.getLastModified());
        assertTrue(metadata.getLastModified().isBefore(Instant.now().plusSeconds(1)));
        assertNull(metadata.getETag());
    }

    // ---------------------------------------------------------------
    // ObjectMetadata.from(Path)
    // ---------------------------------------------------------------

    @Test
    public void fromPath() throws IOException {
        final Path path = Files.createTempFile("test", ".txt");
        path.toFile().deleteOnExit();
        Files.writeString(path, "some content");

        final ObjectMetadata metadata = ObjectMetadata.from(path);

        assertEquals(12, metadata.getContentLength());
        assertEquals("text/plain", metadata.getContentType());
        assertNotNull(metadata.getLastModified());
        assertTrue(metadata.getLastModified().isBefore(Instant.now().plusSeconds(1)));
        assertNull(metadata.getETag());
    }

    @Test
    public void fromPathContentTypeFromName() throws IOException {
        final Path path = Files.createTempFile("test", ".tar.gz");
        path.toFile().deleteOnExit();
        Files.writeString(path, "data");

        final ObjectMetadata metadata = ObjectMetadata.from(path);

        assertEquals(4, metadata.getContentLength());
        assertEquals("application/x-gzip", metadata.getContentType());
    }

    @Test
    public void fromPathUnknownExtension() throws IOException {
        final Path path = Files.createTempFile("test", ".xyz789");
        path.toFile().deleteOnExit();
        Files.writeString(path, "stuff");

        final ObjectMetadata metadata = ObjectMetadata.from(path);

        assertEquals(5, metadata.getContentLength());
        assertEquals("application/octet-stream", metadata.getContentType());
    }

    // ---------------------------------------------------------------
    // toBuilder()
    // ---------------------------------------------------------------

    @Test
    public void toBuilderPreservesAllFields() {
        final ObjectMetadata original = ObjectMetadata.builder()
                .eTag("abc123")
                .contentLength(42)
                .lastModified(Instant.parse("2025-06-15T10:30:00Z"))
                .contentType("text/plain")
                .userMetadata("key1", "value1")
                .build();

        final ObjectMetadata copy = original.toBuilder().build();

        assertEquals("abc123", copy.getETag());
        assertEquals(42, copy.getContentLength());
        assertEquals(Instant.parse("2025-06-15T10:30:00Z"), copy.getLastModified());
        assertEquals("text/plain", copy.getContentType());
        assertEquals("value1", copy.getUserMetadata().get("key1"));
    }

    @Test
    public void toBuilderOverrideField() {
        final ObjectMetadata original = ObjectMetadata.builder()
                .eTag("abc123")
                .contentLength(42)
                .contentType("text/plain")
                .build();

        final ObjectMetadata modified = original.toBuilder()
                .contentType("application/json")
                .contentLength(100)
                .build();

        assertEquals("abc123", modified.getETag());
        assertEquals(100, modified.getContentLength());
        assertEquals("application/json", modified.getContentType());
    }

    @Test
    public void toBuilderAddUserMetadata() {
        final ObjectMetadata original = ObjectMetadata.builder()
                .contentLength(10)
                .userMetadata("existing", "value")
                .build();

        final ObjectMetadata modified = original.toBuilder()
                .userMetadata("new-key", "new-value")
                .build();

        assertEquals("value", modified.getUserMetadata().get("existing"));
        assertEquals("new-value", modified.getUserMetadata().get("new-key"));

        // original is not mutated
        assertNull(original.getUserMetadata().get("new-key"));
    }

    @Test
    public void toBuilderNullUserMetadata() {
        final ObjectMetadata original = ObjectMetadata.builder()
                .contentLength(10)
                .build();

        final ObjectMetadata modified = original.toBuilder()
                .userMetadata("key", "value")
                .build();

        assertEquals("value", modified.getUserMetadata().get("key"));
        assertTrue(original.getUserMetadata().isEmpty());
    }
}
