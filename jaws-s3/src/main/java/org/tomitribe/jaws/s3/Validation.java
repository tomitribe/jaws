/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tomitribe.jaws.s3;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Collects {@link Prefix}, {@link Suffix}, {@link Match} and {@link Filter}
 * annotations from a type and/or method and evaluates them against an
 * {@link S3File}.
 *
 * <p>Annotations are applied in fixed order (cheapest first):
 * <ol>
 *   <li>{@code @Prefix} — {@code file.getName().startsWith(value)}</li>
 *   <li>{@code @Suffix} includes</li>
 *   <li>{@code @Suffix} excludes</li>
 *   <li>{@code @Match} includes</li>
 *   <li>{@code @Match} excludes</li>
 *   <li>{@code @Filter}</li>
 * </ol>
 *
 * <p>Type-level annotations are ANDed with method-level annotations
 * (type first).
 */
public class Validation implements Predicate<S3File> {

    private final Predicate<S3File> predicate;

    private Validation(final Predicate<S3File> predicate) {
        this.predicate = predicate;
    }

    @Override
    public boolean test(final S3File s3File) {
        return predicate.test(s3File);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Class<?> type;
        private Method method;
        private boolean prefix = true;

        public Builder type(final Class<?> type) {
            this.type = type;
            return this;
        }

        public Builder method(final Method method) {
            this.method = method;
            return this;
        }

        public Builder prefix(final boolean prefix) {
            this.prefix = prefix;
            return this;
        }

        public Validation build() {
            Predicate<S3File> predicate = file -> true;

            // Type-level annotations first
            if (type != null) {
                predicate = predicate.and(fromAnnotations(type, false));
            }

            // Method-level annotations second
            if (method != null) {
                predicate = predicate.and(fromAnnotations(method, prefix));
            }

            return new Validation(predicate);
        }

        private static Predicate<S3File> fromAnnotations(final AnnotatedElement element, final boolean applyPrefix) {
            Predicate<S3File> predicate = file -> true;

            // 1. @Prefix — only when applyPrefix is true and element has the annotation
            if (applyPrefix && element.isAnnotationPresent(Prefix.class)) {
                final String value = element.getAnnotation(Prefix.class).value();
                predicate = predicate.and(file -> file.getName().startsWith(value));
            }

            // 2. @Suffix includes
            for (final Suffix suffix : element.getAnnotationsByType(Suffix.class)) {
                if (suffix.exclude()) continue;
                final String[] values = suffix.value();
                predicate = predicate.and(file -> {
                    final String name = file.getName();
                    for (final String s : values) {
                        if (name.endsWith(s)) return true;
                    }
                    return false;
                });
            }

            // 3. @Suffix excludes
            for (final Suffix suffix : element.getAnnotationsByType(Suffix.class)) {
                if (!suffix.exclude()) continue;
                final String[] values = suffix.value();
                predicate = predicate.and(file -> {
                    final String name = file.getName();
                    for (final String s : values) {
                        if (name.endsWith(s)) return false;
                    }
                    return true;
                });
            }

            // 4. @Match includes, then excludes
            for (final Match match : element.getAnnotationsByType(Match.class)) {
                final Predicate<String> regex = Pattern.compile(match.value()).asMatchPredicate();
                if (!match.exclude()) {
                    predicate = predicate.and(file -> regex.test(file.getName()));
                } else {
                    predicate = predicate.and(file -> !regex.test(file.getName()));
                }
            }

            // 5. @Filter
            for (final Filter filter : element.getAnnotationsByType(Filter.class)) {
                predicate = predicate.and(instantiate(filter));
            }

            return predicate;
        }

        private static Predicate<S3File> instantiate(final Filter filter) {
            if (filter == null) return file -> true;
            final Class<? extends Predicate<S3File>> clazz = filter.value();
            try {
                return clazz.newInstance();
            } catch (Exception e) {
                throw new IllegalStateException("Unable to instantiate filter " + clazz, e);
            }
        }
    }
}
