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
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Pre-analyzed annotation metadata for a single proxy method.
 *
 * <p>Built once at proxy creation time; stored in {@code S3Handler} indexed by
 * method. At invocation time, {@link #asAnnotatedElement(Object[])} merges the
 * pre-computed bindings with the runtime args and returns an
 * {@link AnnotatedElement} whose annotation API behaves identically to reading
 * from the method directly — except that dynamically-bound annotations return
 * values extracted from the args array rather than from annotation attributes.
 *
 * <p>This allows all downstream code that already reads from an
 * {@link AnnotatedElement} (such as {@link Validation}) to work unchanged for
 * both static and dynamic annotation usage.
 *
 * <p>Validation errors found during construction are appended to the supplied
 * {@code errors} list. The caller is responsible for checking the list and
 * reporting all errors at once.
 */
public class MethodAnnotations {

    private final Method method;
    private final PrefixBinding prefix;
    private final List<SuffixBinding> suffixes;
    private final List<MatchBinding> matches;

    private MethodAnnotations(final Method method,
                               final PrefixBinding prefix,
                               final List<SuffixBinding> suffixes,
                               final List<MatchBinding> matches) {
        this.method = method;
        this.prefix = prefix;
        this.suffixes = Collections.unmodifiableList(suffixes);
        this.matches = Collections.unmodifiableList(matches);
    }

    public static MethodAnnotations of(final Method method) {
        final Parameter[] params = method.getParameters();
        final List<String> errors = new ArrayList<>();

        PrefixBinding prefix = null;
        final List<SuffixBinding> suffixes = new ArrayList<>();
        final List<MatchBinding> matches = new ArrayList<>();

        // --- @Prefix ---
        // Validate static usage: a bare @Prefix on the method with no value is meaningless.
        if (method.isAnnotationPresent(Prefix.class) && method.getAnnotation(Prefix.class).value().isEmpty()) {
            errors.add(method.getName() + ": @Prefix on method requires a non-empty value");
        }

        for (int i = 0; i < params.length; i++) {
            final Parameter param = params[i];
            if (!param.isAnnotationPresent(Prefix.class)) continue;

            if (method.isAnnotationPresent(Prefix.class)) {
                errors.add(method.getName() + ": @Prefix on both method and parameter is ambiguous");
            } else if (param.getType() != String.class) {
                errors.add(method.getName() + ": @Prefix parameter must be String, found " + param.getType().getSimpleName());
            } else if (prefix != null) {
                errors.add(method.getName() + ": only one @Prefix parameter allowed");
            } else {
                prefix = new PrefixBinding(i);
            }
        }

        // --- @Match ---
        // A @Match String/Pattern opens a group. The next unannotated boolean closes it as the
        // exclude flag. Another @Match String opens a new group, closing the previous one without
        // an exclude. @Match must never appear on a boolean parameter — the pairing is positional.
        if (method.isAnnotationPresent(Match.class) || method.isAnnotationPresent(Matches.class)) {
            for (final Match m : method.getAnnotationsByType(Match.class)) {
                if (m.value().isEmpty()) {
                    errors.add(method.getName() + ": @Match on method requires a non-empty value");
                }
            }
            for (final Parameter param : params) {
                if (param.isAnnotationPresent(Match.class)) {
                    errors.add(method.getName() + ": @Match on both method and parameter is ambiguous");
                    break;
                }
            }
        } else {
            int pendingPatternIndex = -1;

            for (int i = 0; i < params.length; i++) {
                final Parameter param = params[i];

                if (param.isAnnotationPresent(Match.class)) {
                    final Class<?> type = param.getType();
                    if (type == String.class || type == Pattern.class) {
                        if (pendingPatternIndex >= 0) {
                            matches.add(new MatchBinding(pendingPatternIndex, -1));
                        }
                        pendingPatternIndex = i;
                    } else if (type == boolean.class) {
                        errors.add(method.getName() + ": @Match is not allowed on boolean parameters");
                    } else {
                        errors.add(method.getName() + ": @Match parameter must be String or Pattern, found " + type.getSimpleName());
                    }
                } else if (param.getType() == boolean.class && pendingPatternIndex >= 0) {
                    matches.add(new MatchBinding(pendingPatternIndex, i));
                    pendingPatternIndex = -1;
                }
            }

            if (pendingPatternIndex >= 0) {
                matches.add(new MatchBinding(pendingPatternIndex, -1));
            }
        }

        // --- @Suffix ---
        // Same grouping rules as @Match: a @Suffix String opens a group, the next unannotated
        // boolean closes it as the exclude, another @Suffix String opens a new group.
        // @Suffix must never appear on a boolean parameter — the pairing is positional.
        if (method.isAnnotationPresent(Suffix.class) || method.isAnnotationPresent(Suffixes.class)) {
            for (final Suffix s : method.getAnnotationsByType(Suffix.class)) {
                if (s.value().length == 0) {
                    errors.add(method.getName() + ": @Suffix on method requires at least one value");
                }
            }
            for (final Parameter param : params) {
                if (param.isAnnotationPresent(Suffix.class)) {
                    errors.add(method.getName() + ": @Suffix on both method and parameter is ambiguous");
                    break;
                }
            }
        } else {
            int pendingValueIndex = -1;

            for (int i = 0; i < params.length; i++) {
                final Parameter param = params[i];

                if (param.isAnnotationPresent(Suffix.class)) {
                    final Class<?> type = param.getType();
                    if (type == String.class) {
                        if (pendingValueIndex >= 0) {
                            suffixes.add(new SuffixBinding(pendingValueIndex, -1));
                        }
                        pendingValueIndex = i;
                    } else if (type == boolean.class) {
                        errors.add(method.getName() + ": @Suffix is not allowed on boolean parameters");
                    } else {
                        errors.add(method.getName() + ": @Suffix parameter must be String, found " + type.getSimpleName());
                    }
                } else if (param.getType() == boolean.class && pendingValueIndex >= 0) {
                    suffixes.add(new SuffixBinding(pendingValueIndex, i));
                    pendingValueIndex = -1;
                }
            }

            if (pendingValueIndex >= 0) {
                suffixes.add(new SuffixBinding(pendingValueIndex, -1));
            }
        }

        if (!errors.isEmpty()) {
            throw new InvalidAnnotationException(method, errors);
        }

        return new MethodAnnotations(method, prefix, suffixes, matches);
    }

    /**
     * Returns an {@link AnnotatedElement} that merges pre-computed bindings with
     * the supplied runtime args.
     *
     * <p>If no dynamic bindings exist for this method, the underlying
     * {@link Method} is returned directly (zero allocation fast path).
     */
    public AnnotatedElement asAnnotatedElement(final Object[] args) {
        if (prefix == null && suffixes.isEmpty() && matches.isEmpty()) {
            return method;
        }
        return new BoundAnnotations(args);
    }

    private class BoundAnnotations implements AnnotatedElement {
        private final Object[] args;

        private BoundAnnotations(final Object[] args) {
            this.args = args;
        }

        @Override
        public <T extends Annotation> T getAnnotation(final Class<T> annotationType) {
            if (annotationType == Prefix.class && prefix != null) {
                return annotationType.cast(prefix.toAnnotation(args));
            }
            return method.getAnnotation(annotationType);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Annotation> T[] getAnnotationsByType(final Class<T> annotationType) {
            if (annotationType == Match.class && !matches.isEmpty()) {
                return matches.stream()
                        .map(b -> (T) b.toAnnotation(args))
                        .toArray(n -> (T[]) Array.newInstance(annotationType, n));
            }
            if (annotationType == Suffix.class && !suffixes.isEmpty()) {
                return suffixes.stream()
                        .map(b -> (T) b.toAnnotation(args))
                        .toArray(n -> (T[]) Array.newInstance(annotationType, n));
            }
            return method.getAnnotationsByType(annotationType);
        }

        @Override
        public boolean isAnnotationPresent(final Class<? extends Annotation> annotationType) {
            if (annotationType == Prefix.class && prefix != null) return true;
            if (annotationType == Match.class && !matches.isEmpty()) return true;
            if (annotationType == Suffix.class && !suffixes.isEmpty()) return true;
            return method.isAnnotationPresent(annotationType);
        }

        @Override
        public Annotation[] getAnnotations() {
            return method.getAnnotations();
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return method.getDeclaredAnnotations();
        }
    }

    static class PrefixBinding {
        final int paramIndex;

        PrefixBinding(final int paramIndex) {
            this.paramIndex = paramIndex;
        }

        String getValue(final Object[] args) {
            return (String) args[paramIndex];
        }

        Prefix toAnnotation(final Object[] args) {
            final String value = getValue(args);
            return (Prefix) Proxy.newProxyInstance(
                    Prefix.class.getClassLoader(),
                    new Class[]{Prefix.class},
                    (proxy, m, margs) -> {
                        if ("value".equals(m.getName())) return value;
                        if ("annotationType".equals(m.getName())) return Prefix.class;
                        if ("toString".equals(m.getName())) return "@" + Prefix.class.getName() + "(\"" + value + "\")";
                        if ("hashCode".equals(m.getName())) return 127 * "value".hashCode() ^ value.hashCode();
                        if ("equals".equals(m.getName())) return proxy == margs[0];
                        throw new UnsupportedOperationException(m.getName());
                    });
        }
    }

    static class MatchBinding {
        final int patternIndex;
        final int excludeIndex;

        MatchBinding(final int patternIndex, final int excludeIndex) {
            this.patternIndex = patternIndex;
            this.excludeIndex = excludeIndex;
        }

        String getPattern(final Object[] args) {
            final Object val = args[patternIndex];
            return (val instanceof Pattern) ? ((Pattern) val).pattern() : (String) val;
        }

        boolean getExclude(final Object[] args) {
            return excludeIndex >= 0 && (boolean) args[excludeIndex];
        }

        Match toAnnotation(final Object[] args) {
            final String pattern = getPattern(args);
            final boolean exclude = getExclude(args);
            return (Match) Proxy.newProxyInstance(
                    Match.class.getClassLoader(),
                    new Class[]{Match.class},
                    (proxy, m, margs) -> {
                        if ("value".equals(m.getName())) return pattern;
                        if ("exclude".equals(m.getName())) return exclude;
                        if ("annotationType".equals(m.getName())) return Match.class;
                        if ("toString".equals(m.getName())) return "@" + Match.class.getName() + "(value=\"" + pattern + "\", exclude=" + exclude + ")";
                        if ("hashCode".equals(m.getName())) return (127 * "value".hashCode() ^ pattern.hashCode()) + (127 * "exclude".hashCode() ^ Boolean.hashCode(exclude));
                        if ("equals".equals(m.getName())) return proxy == margs[0];
                        throw new UnsupportedOperationException(m.getName());
                    });
        }
    }

    static class SuffixBinding {
        final int valueIndex;
        final int excludeIndex;

        SuffixBinding(final int valueIndex, final int excludeIndex) {
            this.valueIndex = valueIndex;
            this.excludeIndex = excludeIndex;
        }

        String getValue(final Object[] args) {
            return (String) args[valueIndex];
        }

        boolean getExclude(final Object[] args) {
            return excludeIndex >= 0 && (boolean) args[excludeIndex];
        }

        Suffix toAnnotation(final Object[] args) {
            final String value = getValue(args);
            final boolean exclude = getExclude(args);
            return (Suffix) Proxy.newProxyInstance(
                    Suffix.class.getClassLoader(),
                    new Class[]{Suffix.class},
                    (proxy, m, margs) -> {
                        if ("value".equals(m.getName())) return new String[]{value};
                        if ("exclude".equals(m.getName())) return exclude;
                        if ("annotationType".equals(m.getName())) return Suffix.class;
                        if ("toString".equals(m.getName())) return "@" + Suffix.class.getName() + "(value=\"" + value + "\", exclude=" + exclude + ")";
                        if ("hashCode".equals(m.getName())) return (127 * "value".hashCode() ^ value.hashCode()) + (127 * "exclude".hashCode() ^ Boolean.hashCode(exclude));
                        if ("equals".equals(m.getName())) return proxy == margs[0];
                        throw new UnsupportedOperationException(m.getName());
                    });
        }
    }
}
