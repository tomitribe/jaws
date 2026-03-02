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
 * Overrides the S3 key segment used when resolving a proxy method
 * to a child object. By default the method name is used as the key;
 * {@code @Name} allows mapping to an S3 key that cannot be expressed
 * as a valid Java identifier.
 *
 * <h3>Example</h3>
 * <pre>{@code
 * public interface Module extends S3.Dir {
 *     @Name("pom.xml")
 *     PomFile pomXml();
 * }
 * }</pre>
 *
 * <p>Here {@code pomXml()} resolves to the child key {@code "pom.xml"}
 * rather than {@code "pomXml"}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Name {

    /**
     * The S3 key segment to use instead of the method name.
     *
     * @return the key name
     */
    String value();
}
