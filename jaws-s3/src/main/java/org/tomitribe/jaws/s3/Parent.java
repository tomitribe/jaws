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
 * Marks a proxy method as navigating upward in the S3 key hierarchy.
 * The annotated method must return an interface extending {@link S3},
 * and the proxy will be created for the ancestor at the specified depth.
 *
 * <h3>Example</h3>
 * <pre>{@code
 * public interface Section extends S3 {
 *     // Navigate two levels up: section -> src -> module
 *     @Parent(2)
 *     Module module();
 * }
 * }</pre>
 *
 * <p>A value of {@code 1} (the default) navigates to the immediate parent.
 * Higher values skip intermediate levels.
 *
 * @throws NoParentException at runtime if the proxy is already at the
 *         bucket root and no further parent exists
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Parent {

    /**
     * The number of levels to navigate upward. Defaults to {@code 1}.
     *
     * @return the ancestor depth
     */
    int value() default 1;
}
