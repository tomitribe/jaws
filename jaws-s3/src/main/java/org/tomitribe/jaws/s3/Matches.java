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
 * {@link S3File#getName() name} matches the given regular expression.
 * Uses {@link java.util.regex.Pattern#asMatchPredicate()}, so the
 * <b>entire</b> name must match (implied {@code ^} and {@code $}).
 *
 * <pre>{@code
 * public interface Reports extends S3.Dir {
 *     @Matches("daily-\\d{4}-\\d{2}-\\d{2}\\.csv")
 *     Stream<S3File> dailyReports();
 *
 *     @Matches(".*\\.(jpg|png)")
 *     Stream<S3File> images();
 * }
 * }</pre>
 *
 * <h3>Evaluation order</h3>
 *
 * <p>When multiple filter annotations are present, they are applied
 * in order: {@link Prefix @Prefix} (server-side) &rarr;
 * {@link Suffix @Suffix} &rarr; {@code @Matches} &rarr;
 * {@link Filter @Filter}. Each filter only sees entries that passed
 * the previous ones.
 *
 * @see Suffix
 * @see Filter
 * @see Prefix
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Matches {
    String value();
}
