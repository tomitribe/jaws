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
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MethodAnnotationsTest {

    // ---------------------------------------------------------------
    // Test interface — valid usages
    // ---------------------------------------------------------------

    interface Methods {
        // @Prefix
        @Prefix("beta/")
        Stream<S3File> staticPrefix();

        Stream<S3File> dynamicPrefix(@Prefix String prefix);

        // @Match — static
        @Match(".*\\.jpg")
        Stream<S3File> staticMatch();

        // @Match — single group, explicit boolean
        Stream<S3File> dynamicMatch(@Match String pattern);

        Stream<S3File> dynamicMatchWithExclude(@Match String pattern, boolean exclude);

        // @Match — single group, implicit boolean (no annotation on boolean)
        Stream<S3File> dynamicMatchImplicitExclude(@Match String pattern, boolean exclude);

        // @Match — two groups
        Stream<S3File> multipleMatches(@Match String p1, boolean e1, @Match String p2);

        // @Match — single group, explicit boolean
        Stream<S3File> dynamicMatch(@Match Pattern pattern);

        Stream<S3File> dynamicMatchWithExclude(@Match Pattern pattern, boolean exclude);

        // @Match — single group, implicit boolean (no annotation on boolean)
        Stream<S3File> dynamicMatchImplicitExclude(@Match Pattern pattern, boolean exclude);

        // @Match — two groups
        Stream<S3File> multipleMatches(@Match Pattern p1, boolean e1, @Match Pattern p2);

        // @Suffix — static
        @Suffix(".jar")
        Stream<S3File> staticSuffix();

        // @Suffix — single group, explicit boolean
        Stream<S3File> dynamicSuffix(@Suffix String suffix);

        Stream<S3File> dynamicSuffixWithExclude(@Suffix String suffix, boolean exclude);

        // @Suffix — single group, implicit boolean
        Stream<S3File> dynamicSuffixImplicitExclude(@Suffix String suffix, boolean exclude);

        // @Suffix — two groups
        Stream<S3File> multipleSuffixes(@Suffix String s1, boolean e1, @Suffix String s2);

        // No annotations
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

        Stream<S3File> matchBooleanWithoutPattern(@Match boolean exclude);

        @Match(".*\\.jpg")
        Stream<S3File> matchOnBothMethodAndParam(@Match String pattern);

        Stream<S3File> suffixBooleanWithoutString(@Suffix boolean exclude);

        @Suffix(".jar")
        Stream<S3File> suffixOnBothMethodAndParam(@Suffix String value);

        // Correct syntax is either (@Match String pattern) or (@Match String pattern, boolean exclude)
        // Use of (@Match String pattern, @Match boolean exclude) is incorrect
        Stream<S3File> matchAnnotationDuplicated(@Match String pattern, @Match boolean exclude);

        // Correct syntax is either (@Suffix String pattern) or (@Suffix String pattern, boolean exclude)
        // Use of (@Suffix String pattern, @Suffix boolean exclude) is incorrect
        Stream<S3File> suffixAnnotationDuplicated(@Suffix String suffix, @Suffix boolean exclude);

        @Prefix
        Stream<S3File> prefixMissingValue();

        @Match()
        Stream<S3File> matchMissingValue();

        @Match(exclude = true)
        Stream<S3File> matchMissingValue2();

        @Suffix()
        Stream<S3File> suffixMissingValue();

        @Suffix(exclude = true)
        Stream<S3File> suffixMissingValue2();
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
    // @Match — single group, pattern only
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

        assertEquals(".*\\.png", meta.getAnnotationsByType(Match.class)[0].value());
        assertFalse(meta.getAnnotationsByType(Match.class)[0].exclude());
    }

    // ---------------------------------------------------------------
    // @Match — single group, explicit @Match boolean
    // ---------------------------------------------------------------

    @Test
    public void dynamicMatchWithExclude_patternAndExcludeFromArgs() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "dynamicMatchWithExclude", String.class, boolean.class)
                .asAnnotatedElement(new Object[]{".*\\.tmp", true});

        assertEquals(".*\\.tmp", meta.getAnnotationsByType(Match.class)[0].value());
        assertTrue(meta.getAnnotationsByType(Match.class)[0].exclude());
    }

    @Test
    public void dynamicMatchWithExclude_excludeFalse() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "dynamicMatchWithExclude", String.class, boolean.class)
                .asAnnotatedElement(new Object[]{".*\\.tmp", false});

        assertFalse(meta.getAnnotationsByType(Match.class)[0].exclude());
    }

    // ---------------------------------------------------------------
    // @Match — single group, implicit (unannotated) boolean
    // ---------------------------------------------------------------

    @Test
    public void dynamicMatchImplicitExclude_patternAndExcludeFromArgs() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "dynamicMatchImplicitExclude", String.class, boolean.class)
                .asAnnotatedElement(new Object[]{".*\\.tmp", true});

        assertEquals(".*\\.tmp", meta.getAnnotationsByType(Match.class)[0].value());
        assertTrue(meta.getAnnotationsByType(Match.class)[0].exclude());
    }

    // ---------------------------------------------------------------
    // @Match — two groups
    // ---------------------------------------------------------------

    @Test
    public void multipleMatches_twoGroupsReturned() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "multipleMatches", String.class, boolean.class, String.class)
                .asAnnotatedElement(new Object[]{".*\\.jpg", true, ".*\\.png"});

        assertEquals(2, meta.getAnnotationsByType(Match.class).length);
    }

    @Test
    public void multipleMatches_firstGroupWithExclude() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "multipleMatches", String.class, boolean.class, String.class)
                .asAnnotatedElement(new Object[]{".*\\.jpg", true, ".*\\.png"});

        assertEquals(".*\\.jpg", meta.getAnnotationsByType(Match.class)[0].value());
        assertTrue(meta.getAnnotationsByType(Match.class)[0].exclude());
    }

    @Test
    public void multipleMatches_secondGroupNoExclude() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "multipleMatches", String.class, boolean.class, String.class)
                .asAnnotatedElement(new Object[]{".*\\.jpg", true, ".*\\.png"});

        assertEquals(".*\\.png", meta.getAnnotationsByType(Match.class)[1].value());
        assertFalse(meta.getAnnotationsByType(Match.class)[1].exclude());
    }

    // ---------------------------------------------------------------
    // @Match — Pattern overload
    // ---------------------------------------------------------------

    @Test
    public void dynamicMatch_patternType_patternFromArgs() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "dynamicMatch", Pattern.class)
                .asAnnotatedElement(new Object[]{Pattern.compile(".*\\.png")});

        assertEquals(".*\\.png", meta.getAnnotationsByType(Match.class)[0].value());
        assertFalse(meta.getAnnotationsByType(Match.class)[0].exclude());
    }

    @Test
    public void dynamicMatchWithExclude_patternType_patternAndExcludeFromArgs() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "dynamicMatchWithExclude", Pattern.class, boolean.class)
                .asAnnotatedElement(new Object[]{Pattern.compile(".*\\.tmp"), true});

        assertEquals(".*\\.tmp", meta.getAnnotationsByType(Match.class)[0].value());
        assertTrue(meta.getAnnotationsByType(Match.class)[0].exclude());
    }

    @Test
    public void multipleMatches_patternType_twoGroupsReturned() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "multipleMatches", Pattern.class, boolean.class, Pattern.class)
                .asAnnotatedElement(new Object[]{Pattern.compile(".*\\.jpg"), true, Pattern.compile(".*\\.png")});

        assertEquals(2, meta.getAnnotationsByType(Match.class).length);
        assertEquals(".*\\.jpg", meta.getAnnotationsByType(Match.class)[0].value());
        assertTrue(meta.getAnnotationsByType(Match.class)[0].exclude());
        assertEquals(".*\\.png", meta.getAnnotationsByType(Match.class)[1].value());
        assertFalse(meta.getAnnotationsByType(Match.class)[1].exclude());
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
    // @Suffix — single group, value only
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
        assertFalse(meta.getAnnotationsByType(Suffix.class)[0].exclude());
    }

    // ---------------------------------------------------------------
    // @Suffix — single group, explicit @Suffix boolean
    // ---------------------------------------------------------------

    @Test
    public void dynamicSuffixWithExclude_valueAndExcludeFromArgs() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "dynamicSuffixWithExclude", String.class, boolean.class)
                .asAnnotatedElement(new Object[]{"-sources.jar", true});

        assertArrayEquals(new String[]{"-sources.jar"}, meta.getAnnotationsByType(Suffix.class)[0].value());
        assertTrue(meta.getAnnotationsByType(Suffix.class)[0].exclude());
    }

    // ---------------------------------------------------------------
    // @Suffix — single group, implicit (unannotated) boolean
    // ---------------------------------------------------------------

    @Test
    public void dynamicSuffixImplicitExclude_valueAndExcludeFromArgs() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "dynamicSuffixImplicitExclude", String.class, boolean.class)
                .asAnnotatedElement(new Object[]{"-sources.jar", true});

        assertArrayEquals(new String[]{"-sources.jar"}, meta.getAnnotationsByType(Suffix.class)[0].value());
        assertTrue(meta.getAnnotationsByType(Suffix.class)[0].exclude());
    }

    // ---------------------------------------------------------------
    // @Suffix — two groups
    // ---------------------------------------------------------------

    @Test
    public void multipleSuffixes_twoGroupsReturned() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "multipleSuffixes", String.class, boolean.class, String.class)
                .asAnnotatedElement(new Object[]{".jar", true, "-sources.jar"});

        assertEquals(2, meta.getAnnotationsByType(Suffix.class).length);
    }

    @Test
    public void multipleSuffixes_firstGroupWithExclude() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "multipleSuffixes", String.class, boolean.class, String.class)
                .asAnnotatedElement(new Object[]{".jar", true, "-sources.jar"});

        assertArrayEquals(new String[]{".jar"}, meta.getAnnotationsByType(Suffix.class)[0].value());
        assertTrue(meta.getAnnotationsByType(Suffix.class)[0].exclude());
    }

    @Test
    public void multipleSuffixes_secondGroupNoExclude() throws Exception {
        final AnnotatedElement meta = of(Methods.class, "multipleSuffixes", String.class, boolean.class, String.class)
                .asAnnotatedElement(new Object[]{".jar", true, "-sources.jar"});

        assertArrayEquals(new String[]{"-sources.jar"}, meta.getAnnotationsByType(Suffix.class)[1].value());
        assertFalse(meta.getAnnotationsByType(Suffix.class)[1].exclude());
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

    private static final String VALID_PREFIX =
            "\n\n  Valid @Prefix usage:" +
            "\n    @Prefix(\"images/\") Stream<S3File> method()" +
            "\n    Stream<S3File> method(@Prefix String prefix)";

    private static final String VALID_MATCH =
            "\n\n  Valid @Match usage:" +
            "\n    @Match(\".*\\\\.jpg\") Stream<S3File> method()" +
            "\n    Stream<S3File> method(@Match String pattern)" +
            "\n    Stream<S3File> method(@Match String pattern, boolean exclude)" +
            "\n    Stream<S3File> method(@Match Pattern pattern)" +
            "\n    Stream<S3File> method(@Match Pattern pattern, boolean exclude)";

    private static final String VALID_SUFFIX =
            "\n\n  Valid @Suffix usage:" +
            "\n    @Suffix(\".jar\") Stream<S3File> method()" +
            "\n    Stream<S3File> method(@Suffix String suffix)" +
            "\n    Stream<S3File> method(@Suffix String suffix, boolean exclude)";

    @Test
    public void error_prefixOnBothMethodAndParam() throws Exception {
        final InvalidAnnotationException e = invalidMethod("prefixOnBothMethodAndParam", String.class);
        assertEquals(
                "@Prefix(\"foo\") Stream<S3File> prefixOnBothMethodAndParam(@Prefix String)" +
                "\n  @Prefix on both method and parameter is ambiguous" + VALID_PREFIX,
                e.getMessage());
    }

    @Test
    public void error_prefixOnNonStringParam() throws Exception {
        final InvalidAnnotationException e = invalidMethod("prefixOnNonStringParam", int.class);
        assertEquals(
                "Stream<S3File> prefixOnNonStringParam(@Prefix int)" +
                "\n  @Prefix parameter must be String, found int" + VALID_PREFIX,
                e.getMessage());
    }

    @Test
    public void error_multiplePrefixParams() throws Exception {
        final InvalidAnnotationException e = invalidMethod("multiplePrefixParams", String.class, String.class);
        assertEquals(
                "Stream<S3File> multiplePrefixParams(@Prefix String, @Prefix String)" +
                "\n  only one @Prefix parameter allowed" + VALID_PREFIX,
                e.getMessage());
    }

    @Test
    public void error_matchBooleanWithoutPattern() throws Exception {
        final InvalidAnnotationException e = invalidMethod("matchBooleanWithoutPattern", boolean.class);
        assertEquals(
                "Stream<S3File> matchBooleanWithoutPattern(@Match boolean)" +
                "\n  @Match is not allowed on boolean parameters" + VALID_MATCH,
                e.getMessage());
    }

    @Test
    public void error_matchOnBothMethodAndParam() throws Exception {
        final InvalidAnnotationException e = invalidMethod("matchOnBothMethodAndParam", String.class);
        assertEquals(
                "@Match(\".*\\\\.jpg\") Stream<S3File> matchOnBothMethodAndParam(@Match String)" +
                "\n  @Match on both method and parameter is ambiguous" + VALID_MATCH,
                e.getMessage());
    }

    @Test
    public void error_suffixBooleanWithoutString() throws Exception {
        final InvalidAnnotationException e = invalidMethod("suffixBooleanWithoutString", boolean.class);
        assertEquals(
                "Stream<S3File> suffixBooleanWithoutString(@Suffix boolean)" +
                "\n  @Suffix is not allowed on boolean parameters" + VALID_SUFFIX,
                e.getMessage());
    }

    @Test
    public void error_suffixOnBothMethodAndParam() throws Exception {
        final InvalidAnnotationException e = invalidMethod("suffixOnBothMethodAndParam", String.class);
        assertEquals(
                "@Suffix(\".jar\") Stream<S3File> suffixOnBothMethodAndParam(@Suffix String)" +
                "\n  @Suffix on both method and parameter is ambiguous" + VALID_SUFFIX,
                e.getMessage());
    }

    @Test
    public void error_matchAnnotationDuplicated() throws Exception {
        final InvalidAnnotationException e = invalidMethod("matchAnnotationDuplicated", String.class, boolean.class);
        assertEquals(
                "Stream<S3File> matchAnnotationDuplicated(@Match String, @Match boolean)" +
                "\n  @Match is not allowed on boolean parameters" + VALID_MATCH,
                e.getMessage());
    }

    @Test
    public void error_suffixAnnotationDuplicated() throws Exception {
        final InvalidAnnotationException e = invalidMethod("suffixAnnotationDuplicated", String.class, boolean.class);
        assertEquals(
                "Stream<S3File> suffixAnnotationDuplicated(@Suffix String, @Suffix boolean)" +
                "\n  @Suffix is not allowed on boolean parameters" + VALID_SUFFIX,
                e.getMessage());
    }

    @Test
    public void error_prefixMissingValue() throws Exception {
        final InvalidAnnotationException e = invalidMethod("prefixMissingValue");
        assertEquals(
                "@Prefix Stream<S3File> prefixMissingValue()" +
                "\n  @Prefix on method requires a non-empty value" + VALID_PREFIX,
                e.getMessage());
    }

    @Test
    public void error_matchMissingValue() throws Exception {
        final InvalidAnnotationException e = invalidMethod("matchMissingValue");
        assertEquals(
                "@Match Stream<S3File> matchMissingValue()" +
                "\n  @Match on method requires a non-empty value" + VALID_MATCH,
                e.getMessage());
    }

    @Test
    public void error_matchMissingValue2() throws Exception {
        final InvalidAnnotationException e = invalidMethod("matchMissingValue2");
        assertEquals(
                "@Match(exclude = true) Stream<S3File> matchMissingValue2()" +
                "\n  @Match on method requires a non-empty value" + VALID_MATCH,
                e.getMessage());
    }

    @Test
    public void error_suffixMissingValue() throws Exception {
        final InvalidAnnotationException e = invalidMethod("suffixMissingValue");
        assertEquals(
                "@Suffix Stream<S3File> suffixMissingValue()" +
                "\n  @Suffix on method requires at least one value" + VALID_SUFFIX,
                e.getMessage());
    }

    @Test
    public void error_suffixMissingValue2() throws Exception {
        final InvalidAnnotationException e = invalidMethod("suffixMissingValue2");
        assertEquals(
                "@Suffix(exclude = true) Stream<S3File> suffixMissingValue2()" +
                "\n  @Suffix on method requires at least one value" + VALID_SUFFIX,
                e.getMessage());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static MethodAnnotations of(final Method method) {
        return MethodAnnotations.of(method);
    }

    private static MethodAnnotations of(final Class<?> clazz, final String name, final Class<?>... params) throws NoSuchMethodException {
        return of(clazz.getMethod(name, params));
    }

    private static InvalidAnnotationException invalidMethod(final String name, final Class<?>... params) throws NoSuchMethodException {
        final Method method = InvalidMethods.class.getMethod(name, params);
        return assertThrows(InvalidAnnotationException.class, () -> MethodAnnotations.of(method));
    }
}
