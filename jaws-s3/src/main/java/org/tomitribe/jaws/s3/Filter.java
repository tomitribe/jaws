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

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Predicate;

/**
 * Applies an arbitrary client-side {@link java.util.function.Predicate}
 * to filter listing results. The predicate class is instantiated via
 * its no-arg constructor and applied to each {@link S3File} in the
 * listing.
 *
 * <p>{@code @Filter} is the most powerful filtering annotation but
 * also the last to run. When a method or type carries several filter
 * annotations they are evaluated in a fixed order — simplest and
 * cheapest first:
 *
 * <ol>
 *   <li>{@link Prefix @Prefix} — server-side, in the
 *       {@code ListObjects} request</li>
 *   <li>{@link Suffix @Suffix} — client-side,
 *       {@link String#endsWith}</li>
 *   <li>{@link Matches @Matches} — client-side, compiled regex
 *       via {@link java.util.regex.Pattern#asMatchPredicate()}</li>
 *   <li>{@code @Filter} — client-side, arbitrary
 *       {@code Predicate<S3File>}</li>
 * </ol>
 *
 * <p>Because {@code @Filter} runs last, the predicate will only
 * see entries that have already passed {@code @Prefix},
 * {@code @Suffix}, and {@code @Matches}. This is intentional —
 * a predicate can safely assume, for example, that the file name
 * ends with {@code .jar} if {@code @Suffix(".jar")} is also
 * present.
 *
 * <p>{@code @Filter} is repeatable — multiple filters on the same
 * element are combined with AND logic. It can be placed on methods
 * or on interface types. Interface-level filters run before
 * method-level filters.
 *
 * <pre>{@code
 * public interface VersionDir extends S3.Dir {
 *     // @Suffix runs first, then @Filter sees only .jar files
 *     @Suffix(".jar")
 *     @Filter(IsSnapshot.class)
 *     Stream<S3File> snapshotJars();
 * }
 * }</pre>
 *
 * <p>When possible, prefer a simpler annotation. Use
 * {@link Prefix @Prefix} for server-side key filtering,
 * {@link Suffix @Suffix} for extension checks, and
 * {@link Matches @Matches} for name patterns. Reserve
 * {@code @Filter} for criteria that need access to the full
 * {@link S3File} — size, metadata, path components, or other
 * attributes beyond the name.
 *
 * @see Prefix
 * @see Suffix
 * @see Matches
 */
@Repeatable(Filters.class)
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Filter {
    Class<? extends Predicate<S3File>> value();
}
