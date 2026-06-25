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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MethodAnnotationsTest {

    // ---------------------------------------------------------------
    // Test interface — valid usages
    // ---------------------------------------------------------------

    interface Methods {
        @Prefix("beta/")
        Stream<S3File> staticPrefix();

        Stream<S3File> dynamicPrefix(@Prefix String prefix);

        @Match(".*\\.jpg")
        Stream<S3File> staticMatch();

        Stream<S3File> dynamicMatch(@Match String pattern);

        Stream<S3File> dynamicMatchWithExclude(@Match String pattern, @Match boolean exclude);

        @Suffix(".jar")
        Stream<S3File> staticSuffix();

        Stream<S3File> dynamicSuffix(@Suffix String suffix);

        Stream<S3File> dynamicSuffixWithExclude(@Suffix String suffix, @Suffix boolean exclude);

        Stream<S3File> noAnnotations();
    }

    // ---------------------------------------------------------------
    // Test interface — invalid usages (validation error cases)
    // ---------------------------------------------------------------

    interface InvalidMethods {
        @Prefix("foo")
        Stream<S3File> prefixOnBothMethodAndParam(@Prefix String prefix);

        Stream<S3File> prefixOnNonStringParam(@Prefix int value);

        Stream<S3File> multiplePrefixParams(@Prefix String p1, @Prefix String p2);

        Stream<S3File> matchExcludeWithoutPattern(@Match boolean exclude);

        Stream<S3File> multipleMatchPatterns(@Match String p1, @Match String p2);

        @Match(".*\\.jpg")
        Stream<S3File> matchOnBothMethodAndParam(@Match String pattern);

        Stream<S3File> suffixExcludeWithoutValue(@Suffix boolean exclude);

        @Suffix(".jar")
        Stream<S3File> suffixOnBothMethodAndParam(@Suffix String value);
    }

    // ---------------------------------------------------------------
    // @Prefix — static
    // ---------------------------------------------------------------

    @Test
    public void staticPrefix_fastPath() throws Exception {
        final Method method = Methods.class.getMethod("staticPrefix");
        final AnnotatedElement meta = of(method).asAnnotatedElement(null);

        assertSame(method, meta);
        assertTrue(meta.isAnnotationPresent(Prefix.class));
        assertEquals("beta/", meta.getAnnotation(Prefix.class).value());
    }

    // ---------------------------------------------------------------
    // @Prefix — dynamic
    // ---------------------------------------------------------------

    @Test
    public void dynamicPrefix_isAnnotationPresent() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "dynamicPrefix", String.class)
                .asAnnotatedElement(new Object[]{"my/prefix/"});

        assertTrue(meta.isAnnotationPresent(Prefix.class));
    }

    @Test
    public void dynamicPrefix_valueFromArgs() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "dynamicPrefix", String.class)
                .asAnnotatedElement(new Object[]{"my/prefix/"});

        assertEquals("my/prefix/", meta.getAnnotation(Prefix.class).value());
    }

    // ---------------------------------------------------------------
    // @Match — static
    // ---------------------------------------------------------------

    @Test
    public void staticMatch_fastPath() throws Exception {
        final Method method = Methods.class.getMethod("staticMatch");
        final AnnotatedElement meta = of(method).asAnnotatedElement(null);

        assertSame(method, meta);
        assertEquals(1, meta.getAnnotationsByType(Match.class).length);
        assertEquals(".*\\.jpg", meta.getAnnotationsByType(Match.class)[0].value());
    }

    // ---------------------------------------------------------------
    // @Match — dynamic pattern only
    // ---------------------------------------------------------------

    @Test
    public void dynamicMatch_isAnnotationPresent() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "dynamicMatch", String.class)
                .asAnnotatedElement(new Object[]{".*\\.png"});

        assertTrue(meta.isAnnotationPresent(Match.class));
    }

    @Test
    public void dynamicMatch_patternFromArgs() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "dynamicMatch", String.class)
                .asAnnotatedElement(new Object[]{".*\\.png"});

        assertEquals(1, meta.getAnnotationsByType(Match.class).length);
        assertEquals(".*\\.png", meta.getAnnotationsByType(Match.class)[0].value());
    }

    @Test
    public void dynamicMatch_excludeDefaultsFalse() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "dynamicMatch", String.class)
                .asAnnotatedElement(new Object[]{".*\\.png"});

        assertFalse(meta.getAnnotationsByType(Match.class)[0].exclude());
    }

    // ---------------------------------------------------------------
    // @Match — dynamic pattern + exclude
    // ---------------------------------------------------------------

    @Test
    public void dynamicMatchWithExclude_patternFromArgs() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "dynamicMatchWithExclude", String.class, boolean.class)
                .asAnnotatedElement(new Object[]{".*\\.tmp", true});

        assertEquals(".*\\.tmp", meta.getAnnotationsByType(Match.class)[0].value());
    }

    @Test
    public void dynamicMatchWithExclude_excludeFromArgs() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "dynamicMatchWithExclude", String.class, boolean.class)
                .asAnnotatedElement(new Object[]{".*\\.tmp", true});

        assertTrue(meta.getAnnotationsByType(Match.class)[0].exclude());
    }

    @Test
    public void dynamicMatchWithExclude_excludeFalse() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "dynamicMatchWithExclude", String.class, boolean.class)
                .asAnnotatedElement(new Object[]{".*\\.tmp", false});

        assertFalse(meta.getAnnotationsByType(Match.class)[0].exclude());
    }

    // ---------------------------------------------------------------
    // @Suffix — static
    // ---------------------------------------------------------------

    @Test
    public void staticSuffix_fastPath() throws Exception {
        final Method method = Methods.class.getMethod("staticSuffix");
        final AnnotatedElement meta = of(method).asAnnotatedElement(null);

        assertSame(method, meta);
        assertEquals(1, meta.getAnnotationsByType(Suffix.class).length);
        assertArrayEquals(new String[]{".jar"}, meta.getAnnotationsByType(Suffix.class)[0].value());
    }

    // ---------------------------------------------------------------
    // @Suffix — dynamic value only
    // ---------------------------------------------------------------

    @Test
    public void dynamicSuffix_isAnnotationPresent() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "dynamicSuffix", String.class)
                .asAnnotatedElement(new Object[]{".war"});

        assertTrue(meta.isAnnotationPresent(Suffix.class));
    }

    @Test
    public void dynamicSuffix_valueFromArgs() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "dynamicSuffix", String.class)
                .asAnnotatedElement(new Object[]{".war"});

        assertArrayEquals(new String[]{".war"}, meta.getAnnotationsByType(Suffix.class)[0].value());
    }

    @Test
    public void dynamicSuffix_excludeDefaultsFalse() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "dynamicSuffix", String.class)
                .asAnnotatedElement(new Object[]{".war"});

        assertFalse(meta.getAnnotationsByType(Suffix.class)[0].exclude());
    }

    // ---------------------------------------------------------------
    // @Suffix — dynamic value + exclude
    // ---------------------------------------------------------------

    @Test
    public void dynamicSuffixWithExclude_valueFromArgs() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "dynamicSuffixWithExclude", String.class, boolean.class)
                .asAnnotatedElement(new Object[]{"-sources.jar", true});

        assertArrayEquals(new String[]{"-sources.jar"}, meta.getAnnotationsByType(Suffix.class)[0].value());
    }

    @Test
    public void dynamicSuffixWithExclude_excludeFromArgs() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "dynamicSuffixWithExclude", String.class, boolean.class)
                .asAnnotatedElement(new Object[]{"-sources.jar", true});

        assertTrue(meta.getAnnotationsByType(Suffix.class)[0].exclude());
    }

    // ---------------------------------------------------------------
    // No annotations — fast path
    // ---------------------------------------------------------------

    @Test
    public void noAnnotations_returnsMethodItself() throws Exception {
        final Method method = Methods.class.getMethod("noAnnotations");
        final AnnotatedElement meta = of(method).asAnnotatedElement(null);

        assertSame(method, meta);
    }

    // ---------------------------------------------------------------
    // Validation errors
    // ---------------------------------------------------------------

    @Test
    public void error_prefixOnBothMethodAndParam() throws Exception {
        final List<String> errors = errors(InvalidMethods.class, "prefixOnBothMethodAndParam", String.class);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("@Prefix"));
        assertTrue(errors.get(0).contains("ambiguous"));
    }

    @Test
    public void error_prefixOnNonStringParam() throws Exception {
        final List<String> errors = errors(InvalidMethods.class, "prefixOnNonStringParam", int.class);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("@Prefix"));
        assertTrue(errors.get(0).contains("String"));
    }

    @Test
    public void error_multiplePrefixParams() throws Exception {
        final List<String> errors = errors(InvalidMethods.class, "multiplePrefixParams", String.class, String.class);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("@Prefix"));
    }

    @Test
    public void error_matchExcludeWithoutPattern() throws Exception {
        final List<String> errors = errors(InvalidMethods.class, "matchExcludeWithoutPattern", boolean.class);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("@Match"));
    }

    @Test
    public void error_multipleMatchPatterns() throws Exception {
        final List<String> errors = errors(InvalidMethods.class, "multipleMatchPatterns", String.class, String.class);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("@Match"));
    }

    @Test
    public void error_matchOnBothMethodAndParam() throws Exception {
        final List<String> errors = errors(InvalidMethods.class, "matchOnBothMethodAndParam", String.class);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("@Match"));
        assertTrue(errors.get(0).contains("ambiguous"));
    }

    @Test
    public void error_suffixExcludeWithoutValue() throws Exception {
        final List<String> errors = errors(InvalidMethods.class, "suffixExcludeWithoutValue", boolean.class);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("@Suffix"));
    }

    @Test
    public void error_suffixOnBothMethodAndParam() throws Exception {
        final List<String> errors = errors(InvalidMethods.class, "suffixOnBothMethodAndParam", String.class);

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("@Suffix"));
        assertTrue(errors.get(0).contains("ambiguous"));
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static MethodAnnotations of(final Method method) {
        return MethodAnnotations.of(method, new ArrayList<>());
    }

    private static MethodAnnotations of(final Class<?> clazz, final String name, final Class<?>... params) throws NoSuchMethodException {
        return of(clazz.getMethod(name, params));
    }

    private static List<String> errors(final Class<?> clazz, final String name, final Class<?>... params) throws NoSuchMethodException {
        final List<String> errors = new ArrayList<>();
        MethodAnnotations.of(clazz.getMethod(name, params), errors);
        return errors;
    }
}
