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
 * {@link S3File#getName() name} ends with one of the given suffixes.
 * Multiple values are OR'd — a file matches if its name ends with
 * <b>any</b> of them.
 *
 * <p>This is a shorthand for the common case of filtering by file
 * extension. For more complex name patterns use {@link Match @Match}.
 * For arbitrary criteria use {@link Filter @Filter}.
 *
 * <p>In addition to filtering listings, {@code @Suffix} also
 * <b>validates input</b> on single-arg proxy methods. When the return
 * type carries {@code @Suffix}, the input name must end with one of
 * the specified suffixes or an {@link IllegalArgumentException} is
 * thrown.
 *
 * <p>{@code @Suffix} is repeatable. When {@link #exclude()} is
 * {@code false} (the default), the annotation <b>includes</b> entries
 * matching any of its suffixes. When {@code exclude = true}, it
 * <b>excludes</b> entries matching any of its suffixes. All includes are
 * OR'd together, all excludes are OR'd together, and an entry passes if
 * it matches an include and does not match an exclude:
 *
 * <pre>{@code
 * public interface Assets extends S3.Dir {
 *     @Suffix(".css")
 *     Stream<S3File> stylesheets();
 *
 *     @Suffix({".jpg", ".png", ".gif"})
 *     Stream<S3File> images();
 *
 *     // All .jar files except sources and javadoc jars
 *     @Suffix(".jar")
 *     @Suffix(value = {"-sources.jar", "-javadoc.jar"}, exclude = true)
 *     Stream<S3File> binaryJars();
 * }
 * }</pre>
 *
 * <h3>Evaluation order</h3>
 *
 * <p>Different annotation kinds are AND'd, applied in order:
 * {@link Prefix @Prefix} (server-side) &rarr; {@code @Suffix} &rarr;
 * {@link Match @Match} &rarr; {@link Filter @Filter}. Each kind only sees
 * entries that passed the previous ones. Repeating {@code @Suffix} is the
 * same as listing more values in one annotation.
 *
 * @see Match
 * @see Filter
 * @see Prefix
 */
@Repeatable(Suffixes.class)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Suffix {
    String[] value() default {};

    boolean exclude() default false;
}
