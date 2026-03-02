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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a proxy method as performing a recursive walk of the S3
 * key hierarchy, similar to {@link java.nio.file.Files#walk}.
 * The annotated method must return {@code Stream<S3File>},
 * {@code List<S3File>}, {@code S3File[]}, or a collection type
 * whose element type is {@link S3File} or an interface extending
 * {@link S3}.
 *
 * <h3>Example</h3>
 * <pre>{@code
 * public interface Work extends S3.Dir {
 *     @Walk
 *     Stream<S3File> everything();
 *
 *     @Walk(maxDepth = 2)
 *     Stream<S3File> shallow();
 *
 *     @Walk(minDepth = 2, maxDepth = 2)
 *     Stream<S3File> exactlyTwoLevelsDeep();
 * }
 * }</pre>
 *
 * <p>Without {@code @Walk}, stream-returning methods use a
 * single-level delimiter-based listing. With {@code @Walk},
 * the listing descends recursively into child prefixes.
 *
 * <h3>AWS request cost</h3>
 *
 * <p>Each level of the walk issues one {@code ListObjects} request
 * per prefix (directory) visited, using {@code delimiter="/"} to
 * discover both objects and child prefixes at that level. If a
 * single response is truncated (more than 1,000 keys), additional
 * paginated requests are made automatically for that prefix.
 *
 * <p>The total number of requests is therefore:
 * <pre>
 *   requests = sum of ceil(entries / 1000) for each prefix visited
 * </pre>
 *
 * <p>For example, a bucket with the structure:
 * <pre>
 *   photos/
 *     2024/
 *       jan/   (50 files)
 *       feb/   (50 files)
 *     2025/
 *       jan/   (50 files)
 * </pre>
 *
 * <p>An unlimited {@code @Walk} from {@code photos/} issues
 * <b>5 requests</b>: one for {@code photos/}, one each for
 * {@code 2024/} and {@code 2025/}, and one each for the three
 * month prefixes. Each request returns both the files (contents)
 * and subdirectories (commonPrefixes) at that level.
 *
 * <p>Limiting depth with {@code maxDepth} reduces the number of
 * prefixes descended into and therefore the number of requests.
 * {@code minDepth} does <em>not</em> reduce requests — all levels
 * are still traversed; results are simply filtered before being
 * returned to the caller.
 *
 * <p>By contrast, a method <em>without</em> {@code @Walk} issues
 * a single {@code ListObjects} request (plus pagination if needed)
 * for the immediate level only.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Walk {

    /**
     * Maximum depth to traverse. A value of {@code -1} (the default)
     * means unlimited depth.
     *
     * @return the maximum depth, or {@code -1} for unlimited
     */
    int maxDepth() default -1;

    /**
     * Minimum depth at which results are included. Results at
     * shallower depths are traversed but not returned. Defaults
     * to {@code 0}.
     *
     * @return the minimum depth
     */
    int minDepth() default 0;
}
