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

    public static MethodAnnotations of(final Method method, final List<String> errors) {
        final Parameter[] params = method.getParameters();

        PrefixBinding prefix = null;
        final List<SuffixBinding> suffixes = new ArrayList<>();
        final List<MatchBinding> matches = new ArrayList<>();

        // --- @Prefix ---
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
        {
            int patternIndex = -1;
            int excludeIndex = -1;
            boolean hasMatchParam = false;

            for (int i = 0; i < params.length; i++) {
                final Parameter param = params[i];
                if (!param.isAnnotationPresent(Match.class)) continue;
                hasMatchParam = true;

                final Class<?> type = param.getType();
                if (type == String.class || type == Pattern.class) {
                    if (patternIndex >= 0) {
                        errors.add(method.getName() + ": only one @Match pattern parameter allowed");
                    } else {
                        patternIndex = i;
                    }
                } else if (type == boolean.class) {
                    if (excludeIndex >= 0) {
                        errors.add(method.getName() + ": only one @Match exclude parameter allowed");
                    } else {
                        excludeIndex = i;
                    }
                } else {
                    errors.add(method.getName() + ": @Match parameter must be String, Pattern, or boolean, found " + type.getSimpleName());
                }
            }

            if (hasMatchParam) {
                if (method.isAnnotationPresent(Match.class) || method.isAnnotationPresent(Matches.class)) {
                    errors.add(method.getName() + ": @Match on both method and parameter is ambiguous");
                } else if (patternIndex < 0) {
                    errors.add(method.getName() + ": @Match exclude parameter requires a @Match pattern parameter");
                } else {
                    matches.add(new MatchBinding(patternIndex, excludeIndex));
                }
            }
        }

        // --- @Suffix ---
        {
            int valueIndex = -1;
            int excludeIndex = -1;
            boolean hasSuffixParam = false;

            for (int i = 0; i < params.length; i++) {
                final Parameter param = params[i];
                if (!param.isAnnotationPresent(Suffix.class)) continue;
                hasSuffixParam = true;

                final Class<?> type = param.getType();
                if (type == String.class) {
                    if (valueIndex >= 0) {
                        errors.add(method.getName() + ": only one @Suffix value parameter allowed");
                    } else {
                        valueIndex = i;
                    }
                } else if (type == boolean.class) {
                    if (excludeIndex >= 0) {
                        errors.add(method.getName() + ": only one @Suffix exclude parameter allowed");
                    } else {
                        excludeIndex = i;
                    }
                } else {
                    errors.add(method.getName() + ": @Suffix parameter must be String or boolean, found " + type.getSimpleName());
                }
            }

            if (hasSuffixParam) {
                if (method.isAnnotationPresent(Suffix.class) || method.isAnnotationPresent(Suffixes.class)) {
                    errors.add(method.getName() + ": @Suffix on both method and parameter is ambiguous");
                } else if (valueIndex < 0) {
                    errors.add(method.getName() + ": @Suffix exclude parameter requires a @Suffix value parameter");
                } else {
                    suffixes.add(new SuffixBinding(valueIndex, excludeIndex));
                }
            }
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
