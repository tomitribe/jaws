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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Collects {@link Prefix}, {@link Suffix}, {@link Match} and {@link Filter}
 * annotations from a type and/or method and evaluates them against an
 * {@link S3File}.
 *
 * <p>Annotation kinds are AND'd, applied in fixed order (cheapest first):
 * <ol>
 *   <li>{@code @Prefix} — {@code file.getName().startsWith(value)}</li>
 *   <li>{@code @Suffix}</li>
 *   <li>{@code @Match}</li>
 *   <li>{@code @Filter}</li>
 * </ol>
 *
 * <p>Within {@code @Suffix} and {@code @Match}, all includes are OR'd
 * together, all excludes are OR'd together, and an entry passes if it
 * matches an include and does not match an exclude. Repeating the
 * annotation is the same as listing more values in one annotation.
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
        private AnnotatedElement element;
        private boolean prefix = true;

        public Builder type(final Class<?> type) {
            this.type = type;
            return this;
        }

        public Builder method(final Method method) {
            this.element = method;
            return this;
        }

        public Builder element(final AnnotatedElement element) {
            this.element = element;
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
            if (element != null) {
                predicate = predicate.and(fromAnnotations(element, prefix));
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

            // 2. @Suffix — includes OR'd, excludes OR'd, include AND NOT exclude
            final List<String> suffixIncludes = new ArrayList<>();
            final List<String> suffixExcludes = new ArrayList<>();
            for (final Suffix suffix : element.getAnnotationsByType(Suffix.class)) {
                (suffix.exclude() ? suffixExcludes : suffixIncludes).addAll(Arrays.asList(suffix.value()));
            }
            if (!suffixIncludes.isEmpty()) predicate = predicate.and(file -> endsWithAny(file.getName(), suffixIncludes));
            if (!suffixExcludes.isEmpty()) predicate = predicate.and(file -> !endsWithAny(file.getName(), suffixExcludes));

            // 3. @Match — includes OR'd, excludes OR'd, include AND NOT exclude
            final List<Predicate<String>> matchIncludes = new ArrayList<>();
            final List<Predicate<String>> matchExcludes = new ArrayList<>();
            for (final Match match : element.getAnnotationsByType(Match.class)) {
                (match.exclude() ? matchExcludes : matchIncludes).add(Pattern.compile(match.value()).asMatchPredicate());
            }
            if (!matchIncludes.isEmpty()) predicate = predicate.and(file -> matchesAny(file.getName(), matchIncludes));
            if (!matchExcludes.isEmpty()) predicate = predicate.and(file -> !matchesAny(file.getName(), matchExcludes));

            // 4. @Filter
            for (final Filter filter : element.getAnnotationsByType(Filter.class)) {
                predicate = predicate.and(instantiate(filter));
            }

            return predicate;
        }

        private static boolean endsWithAny(final String name, final List<String> suffixes) {
            for (final String suffix : suffixes) {
                if (name.endsWith(suffix)) return true;
            }
            return false;
        }

        private static boolean matchesAny(final String name, final List<Predicate<String>> patterns) {
            for (final Predicate<String> pattern : patterns) {
                if (pattern.test(name)) return true;
            }
            return false;
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
