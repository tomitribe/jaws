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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Filters listing results on the client side to entries whose
 * {@link S3File#getName() name} ends with one of the given suffixes.
 * Multiple values are OR'd — a file matches if its name ends with
 * <b>any</b> of them.
 *
 * <p>This is a shorthand for the common case of filtering by file
 * extension. For more complex name patterns use {@link Matches @Matches}.
 * For arbitrary criteria use {@link Filter @Filter}.
 *
 * <pre>{@code
 * public interface Assets extends S3.Dir {
 *     @Suffix(".css")
 *     Stream<S3File> stylesheets();
 *
 *     @Suffix({".jpg", ".png", ".gif"})
 *     Stream<S3File> images();
 * }
 * }</pre>
 *
 * <h3>Evaluation order</h3>
 *
 * <p>When multiple filter annotations are present, they are applied
 * in order: {@link Prefix @Prefix} (server-side) &rarr;
 * {@code @Suffix} &rarr; {@link Matches @Matches} &rarr;
 * {@link Filter @Filter}. Each filter only sees entries that passed
 * the previous ones.
 *
 * @see Matches
 * @see Filter
 * @see Prefix
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Suffix {
    String[] value();
}
