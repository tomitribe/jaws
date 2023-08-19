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

import java.util.Objects;

public class Path {
    private final String name;
    private final String absoluteName;
    private final String parent;

    private Path(final String name, final String absoluteName, final String parent) {
        this.name = name;
        this.absoluteName = absoluteName;
        this.parent = parent;
    }

    public static Path fromKey(String key) {
        key = normalize(key);
        if ("".equals(key)) return ROOT;
        final int lastSlash = key.lastIndexOf('/') + 1;
        if (lastSlash == 0) return new Path(key, key, null);

        final String name = key.substring(lastSlash);
        final String parent = key.substring(0, lastSlash);
        return new Path(name, key, parent);
    }

    private static String normalize(final String key) {
        if (!key.endsWith("/")) return key;
        return normalize(key.substring(0, key.length() - 1));
    }

    public static Path root() {
        return ROOT;
    }

    public String getName() {
        return name;
    }

    public String getAbsoluteName() {
        return absoluteName;
    }

    public String getSearchPrefix() {
        return absoluteName + "/";
    }

    public Path getParent() {
        return parent == null ? ROOT : Path.fromKey(parent);
    }

    public Path getChild(final String name) {
        if (name.contains("/")) return Path.fromKey(absoluteName + "/" + name);

        return new Path(name, absoluteName + "/" + name, absoluteName);
    }

    @Override
    public String toString() {
        return absoluteName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Path path = (Path) o;
        return absoluteName.equals(path.absoluteName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(absoluteName);
    }

    public static final Path ROOT = new Path("", "", null) {
        @Override
        public Path getParent() {
            return null;
        }

        @Override
        public Path getChild(final String name) {
            return Path.fromKey(name);
        }

        @Override
        public String getSearchPrefix() {
            return null;
        }
    };
}
