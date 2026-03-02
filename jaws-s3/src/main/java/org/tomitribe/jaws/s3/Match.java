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

/**
 * Filters listing results on the client side to entries whose
 * {@link S3File#getName() name} matches the given regular expression.
 * Uses {@link java.util.regex.Pattern#asMatchPredicate()}, so the
 * <b>entire</b> name must match (implied {@code ^} and {@code $}).
 *
 * <p>{@code @Match} is repeatable. When {@link #exclude()} is
 * {@code false} (the default), the annotation <b>includes</b> entries
 * matching the pattern. When {@code exclude = true}, it <b>excludes</b>
 * entries matching the pattern. Includes are applied before excludes,
 * so you can combine them:
 *
 * <pre>{@code
 * public interface Reports extends S3.Dir {
 *     @Match("daily-\\d{4}-\\d{2}-\\d{2}\\.csv")
 *     Stream<S3File> dailyReports();
 *
 *     @Match(".*\\.(jpg|png)")
 *     Stream<S3File> images();
 *
 *     // All CSS files except reset.css
 *     @Match(".*\\.css")
 *     @Match(value = "reset\\.css", exclude = true)
 *     Stream<S3File> cssExceptReset();
 * }
 * }</pre>
 *
 * <h3>Evaluation order</h3>
 *
 * <p>When multiple filter annotations are present, they are applied
 * in order: {@link Prefix @Prefix} (server-side) &rarr;
 * {@link Suffix @Suffix} includes &rarr; {@link Suffix @Suffix}
 * excludes &rarr; {@code @Match} includes &rarr; {@code @Match}
 * excludes &rarr; {@link Filter @Filter}. Each filter only sees
 * entries that passed the previous ones.
 *
 * @see Suffix
 * @see Filter
 * @see Prefix
 */
@Repeatable(Matches.class)
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Match {
    String value();

    boolean exclude() default false;
}
