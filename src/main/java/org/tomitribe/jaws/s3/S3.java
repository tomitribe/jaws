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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class S3 {

    private S3() {
    }

    static <T> T of(final Class<T> clazz, final S3Bucket bucket) {
        return of(clazz, bucket.asFile());
    }

    static <T> T of(final Class<T> clazz, final S3File directory) {
        return (T) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class[]{clazz},
                new S3.S3Handler(directory)
        );
    }

    private static class S3Handler implements InvocationHandler {
        private final S3File dir;

        public S3Handler(final S3File dir) {
            this.dir = dir;
        }

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
            final Class<?> returnType = method.getReturnType();

            if (returnType.isInterface() && args != null && args.length == 1 && args[0] instanceof String) {
                return S3.of(returnType, dir.getFile((String) args[0]));
            }

            if (returnType.isInterface() && args == null) {
                return S3.of(returnType, dir);
            }

            throw new UnsupportedOperationException(method.toGenericString());
        }
    }
}
