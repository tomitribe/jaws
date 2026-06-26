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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class InvalidAnnotationException extends IllegalArgumentException {

    private final Method method;
    private final List<String> errors;

    public InvalidAnnotationException(final Method method, final List<String> errors) {
        super(buildMessage(method, errors));
        this.method = method;
        this.errors = Collections.unmodifiableList(errors);
    }

    public Method getMethod() {
        return method;
    }

    public List<String> getErrors() {
        return errors;
    }

    private static String buildMessage(final Method method, final List<String> errors) {
        final StringBuilder sb = new StringBuilder();
        sb.append(signature(method));
        for (final String error : errors) {
            sb.append("\n  ").append(error);
        }

        final boolean needsPrefix = errors.stream().anyMatch(e -> e.contains("@Prefix"));
        final boolean needsMatch  = errors.stream().anyMatch(e -> e.contains("@Match"));
        final boolean needsSuffix = errors.stream().anyMatch(e -> e.contains("@Suffix"));

        if (needsPrefix) {
            sb.append("\n\n  Valid @Prefix usage:");
            sb.append("\n    @Prefix(\"images/\") Stream<S3File> method()");
            sb.append("\n    Stream<S3File> method(@Prefix String prefix)");
        }
        if (needsMatch) {
            sb.append("\n\n  Valid @Match usage:");
            sb.append("\n    @Match(\".*\\\\.jpg\") Stream<S3File> method()");
            sb.append("\n    Stream<S3File> method(@Match String pattern)");
            sb.append("\n    Stream<S3File> method(@Match String pattern, boolean exclude)");
            sb.append("\n    Stream<S3File> method(@Match Pattern pattern)");
            sb.append("\n    Stream<S3File> method(@Match Pattern pattern, boolean exclude)");
        }
        if (needsSuffix) {
            sb.append("\n\n  Valid @Suffix usage:");
            sb.append("\n    @Suffix(\".jar\") Stream<S3File> method()");
            sb.append("\n    Stream<S3File> method(@Suffix String suffix)");
            sb.append("\n    Stream<S3File> method(@Suffix String suffix, boolean exclude)");
        }

        return sb.toString();
    }

    static String signature(final Method method) {
        final StringBuilder sb = new StringBuilder();

        if (method.isAnnotationPresent(Prefix.class)) {
            sb.append(format(method.getAnnotation(Prefix.class))).append(" ");
        }
        for (final Match m : method.getAnnotationsByType(Match.class)) {
            sb.append(format(m)).append(" ");
        }
        for (final Suffix s : method.getAnnotationsByType(Suffix.class)) {
            sb.append(format(s)).append(" ");
        }

        sb.append(simpleName(method.getGenericReturnType().getTypeName()));
        sb.append(" ").append(method.getName()).append("(");

        final Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(", ");
            for (final Annotation ann : params[i].getAnnotations()) {
                if (ann instanceof Prefix || ann instanceof Match || ann instanceof Suffix) {
                    sb.append(format(ann)).append(" ");
                }
            }
            sb.append(params[i].getType().getSimpleName());
        }

        sb.append(")");
        return sb.toString();
    }

    static String simpleName(final String typeName) {
        return typeName.replaceAll("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*\\.", "");
    }

    static String format(final Annotation ann) {
        if (ann instanceof Prefix) {
            final Prefix p = (Prefix) ann;
            return p.value().isEmpty() ? "@Prefix" : "@Prefix(\"" + escape(p.value()) + "\")";
        }
        if (ann instanceof Match) {
            final Match m = (Match) ann;
            if (!m.value().isEmpty() && m.exclude()) return "@Match(value = \"" + escape(m.value()) + "\", exclude = true)";
            if (!m.value().isEmpty()) return "@Match(\"" + escape(m.value()) + "\")";
            if (m.exclude()) return "@Match(exclude = true)";
            return "@Match";
        }
        if (ann instanceof Suffix) {
            final Suffix s = (Suffix) ann;
            final String[] vals = s.value();
            if (vals.length == 0 && s.exclude()) return "@Suffix(exclude = true)";
            if (vals.length == 0) return "@Suffix";
            final String joined = Arrays.stream(vals).map(v -> "\"" + escape(v) + "\"").collect(Collectors.joining(", "));
            if (vals.length == 1 && !s.exclude()) return "@Suffix(\"" + escape(vals[0]) + "\")";
            if (vals.length == 1) return "@Suffix(value = \"" + escape(vals[0]) + "\", exclude = true)";
            return s.exclude() ? "@Suffix(value = {" + joined + "}, exclude = true)" : "@Suffix({" + joined + "})";
        }
        return "@" + ann.annotationType().getSimpleName();
    }

    private static String escape(final String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
