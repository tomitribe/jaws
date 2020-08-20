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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class PathTest {

    @Test
    public void noParent() throws Exception {
        final Path path = Path.fromKey("colors.txt");

        assertSame(Path.ROOT, path.getParent());
        assertEquals("colors.txt", path.getName());
        assertEquals("colors.txt", path.getAbsoluteName());
    }

    @Test
    public void hasParent() throws Exception {
        final Path path = Path.fromKey("colors/red.txt");
        final Path parent = Path.fromKey("colors/");

        assertEquals(parent.getAbsoluteName(), path.getParent().getAbsoluteName());
        assertEquals("red.txt", path.getName());
        assertEquals("colors/red.txt", path.getAbsoluteName());
    }

    @Test
    public void getParent() throws Exception {
        final Path path = Path.fromKey("colors/red.txt");

        final Path expected = Path.fromKey("colors/");
        final Path actual = path.getParent();

        assertEquals(expected.getAbsoluteName(), actual.getAbsoluteName());
    }

    @Test
    public void getParentWithNoParent() throws Exception {
        final Path path = Path.fromKey("red.txt");

        assertSame(Path.ROOT, path.getParent());
    }

    @Test
    public void getChild() throws Exception {
        final Path path = Path.fromKey("colors/");
        final Path child = path.getChild("green.txt");

        assertEquals("colors/green.txt", child.getAbsoluteName());
        assertEquals("green.txt", child.getName());
        assertEquals("colors", child.getParent().getAbsoluteName());

    }

    /**
     * The child path itself contains directories
     */
    @Test
    public void getChildNested() throws Exception {
        final Path path = Path.fromKey("colors/");
        final Path child = path.getChild("red/crimson.txt");

        assertEquals("colors/red/crimson.txt", child.getAbsoluteName());
        assertEquals("crimson.txt", child.getName());
        assertEquals("colors/red", child.getParent().getAbsoluteName());
    }

    /**
     * The child path itself contains directories
     */
    @Test
    public void root() throws Exception {
        final Path root = Path.root();

        assertSame(Path.ROOT, root);
        assertNull(root.getParent());

        assertEquals("", root.getAbsoluteName());
        assertEquals("", root.getName());
        assertNull(root.getParent());

        final Path child = root.getChild("red/");

        assertEquals("red", child.getAbsoluteName());
        assertEquals("red", child.getName());
        assertSame(Path.ROOT, child.getParent());
    }

    /**
     * If "" is used as the name, we should get the ROOT
     */
    @Test
    public void emptyString() throws Exception {
        final Path root = Path.fromKey("");

        assertSame(Path.ROOT, root);
    }

    /**
     * If "" is used as the name, we should get the ROOT
     */
    @Test
    public void slash() throws Exception {
        final Path root = Path.fromKey("/");

        assertSame(Path.ROOT, root);
    }

    /**
     * The child path itself contains directories
     */
    @Test
    public void fromKeyWithSlash() throws Exception {
        final Path root = Path.fromKey("colors/");

        assertEquals("colors", root.getAbsoluteName());
        assertEquals("colors", root.getName());
        assertSame(Path.ROOT, root.getParent());
    }

}
