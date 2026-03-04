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
 * Narrows a listing or walk to only S3 keys that begin with the
 * given prefix. The value is relative — it is appended to the
 * current proxy's position in the key hierarchy. The prefix is
 * sent server-side in the {@code ListObjects} request, so keys
 * that do not match are never returned by AWS and never consume
 * bandwidth or processing.
 *
 * <p>Consider a Maven repository stored in S3:
 * <pre>
 *   repository/
 *     org/apache/maven/maven-core/3.9.6/maven-core-3.9.6.jar
 *     org/apache/maven/maven-core/3.9.6/maven-core-3.9.6.pom
 *     org/apache/tomcat/tomcat/10.1.18/tomcat-10.1.18.jar
 *     com/google/guava/guava/33.0/guava-33.0.jar
 *     junit/junit/4.13.2/junit-4.13.2.jar
 * </pre>
 *
 * <h3>Flat listing</h3>
 *
 * <p>On a method without {@link Recursive @Recursive}, the prefix
 * narrows an immediate listing. For example, listing only artifacts
 * under the {@code org/apache} namespace:
 * <pre>{@code
 * public interface Repository extends S3.Dir {
 *     @Prefix("org/apache")
 *     Stream<S3File> apacheArtifacts();
 * }
 * }</pre>
 *
 * <h3>Recursive listing</h3>
 *
 * <p>Combined with {@link Recursive @Recursive}, the prefix limits
 * <em>which keys are returned</em>, reducing both the bandwidth
 * and the volume of results:
 * <pre>{@code
 * public interface Repository extends S3.Dir {
 *     // Recursively list only keys under the org/ namespace
 *     // — com/ and junit/ are never fetched from AWS
 *     @Recursive
 *     @Prefix("org/")
 *     Stream<S3File> orgArtifacts();
 * }
 * }</pre>
 *
 * <p>Without {@code @Prefix}, the recursive listing returns every
 * key in the directory. With {@code @Prefix("org/")}, only keys
 * under {@code org/} are returned by AWS. In a large repository
 * with thousands of groupIds, this can significantly reduce the
 * data transferred.
 *
 * <h3>Partial prefixes</h3>
 *
 * <p>The prefix does not need to end with a delimiter — it can be
 * any key prefix, including a partial name. This is useful for
 * matching a subset of entries within a single directory. For
 * example, listing only Tomcat versions that start with {@code 10.}:
 * <pre>{@code
 * public interface Tomcat extends S3.Dir {
 *     // Only version directories starting with "10."
 *     // Matches 10.1.18/, 10.1.19/, etc.
 *     // Skips 9.0.85/, 11.0.0/, etc.
 *     @Prefix("10.")
 *     Stream<S3File> version10();
 * }
 *
 * S3File tomcatDir = bucket.getFile("org/apache/tomcat/tomcat");
 * Tomcat tomcat = tomcatDir.as(Tomcat.class);
 * tomcat.version10().forEach(v -> System.out.println(v.getName()));
 * }</pre>
 *
 * <p>Because the prefix is applied server-side, AWS returns only
 * the matching keys. A directory with hundreds of version entries
 * can be efficiently narrowed to just the relevant subset.
 *
 * <h3>Input validation on single-arg methods</h3>
 *
 * <p>When {@code @Prefix} is placed on a single-arg proxy method,
 * it also <b>validates</b> that the input name starts with the
 * prefix. If not, an {@link IllegalArgumentException} is thrown.
 * For listing methods, the prefix is applied server-side; for
 * single-arg methods, it is checked client-side.
 *
 * @see Recursive
 * @see Delimiter
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Prefix {
    String value();
}
