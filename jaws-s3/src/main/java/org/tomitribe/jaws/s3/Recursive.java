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
 * Marks a listing method as recursive — all descendants are returned,
 * not just immediate children.
 *
 * <p>Without this annotation, all listing methods (returning
 * {@code Stream}, {@code List}, {@code Set}, {@code Collection},
 * or arrays) use a delimiter-based listing that returns only
 * immediate children at one level.
 *
 * <h3>Behavior by element type</h3>
 *
 * <ul>
 *   <li>For non-{@link S3.Dir} element types (including
 *       {@link S3File} and {@link S3.File}): uses {@code files()} —
 *       a single flat request returning all descendant objects</li>
 *   <li>For {@link S3.Dir} element types: uses {@code walk()} —
 *       a multi-request tree traversal returning directories only</li>
 * </ul>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * public interface Processed extends S3.Dir {
 *     // All parquet files across all partitions
 *     @Recursive
 *     @Filter(IsParquet.class)
 *     Stream<S3File> allParquetFiles();
 *
 *     // All subdirectories recursively
 *     @Recursive
 *     Stream<S3.Dir> layout();
 * }
 * }</pre>
 *
 * <p>There is no depth control. For depth-limited traversal,
 * use the {@link S3File#walk} methods directly.
 *
 * @see S3.Dir#files()
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Recursive {
}
