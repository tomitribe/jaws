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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PathTest {

    @Test
    public void noParent() throws Exception {
        final Path path = Path.fromKey("colors.txt");

        assertNull(path.getParent());
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

        assertNull(path.getParent());
    }

    @Test
    public void isDirectory() throws Exception {
        final Path path = Path.fromKey("colors/red.txt");

        assertFalse(path.isDirectory());
        assertTrue(path.getParent().isDirectory());
    }

    @Test
    public void getChild() throws Exception {
        final Path path = Path.fromKey("colors/");
        final Path child = path.getChild("green.txt");

        assertEquals("colors/green.txt", child.getAbsoluteName());
        assertEquals("green.txt", child.getName());
        assertEquals("colors/", child.getParent().getAbsoluteName());

    }

    @Test
    public void getChildAsFile() throws Exception {
        final Path path = Path.fromKey("red.txt");

        try {
            path.getChild("foo.txt");
            fail("UnsupportedOperationException should have been thrown");
        } catch (UnsupportedOperationException e) {
            assertEquals("Path 'red.txt' is a not directory and cannot have child 'foo.txt'", e.getMessage());
        }
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
        assertEquals("colors/red/", child.getParent().getAbsoluteName());
    }

}
