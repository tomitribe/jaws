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

import com.amazonaws.services.s3.model.ListObjectsRequest;
import org.tomitribe.util.dir.Name;
import org.tomitribe.util.dir.Walk;
import org.tomitribe.util.reflect.Generics;

import java.io.FileNotFoundException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface S3 {

    S3File get();

    S3File parent();

    S3File file(String name);

    Stream<S3File> files();

    static <T> T of(final Class<T> clazz, final S3Bucket bucket) {
        return of(clazz, bucket.asFile());
    }

    static <T> T of(final Class<T> clazz, final S3File file) {
        return (T) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class[]{clazz},
                new S3.S3Handler(file)
        );
    }

    class S3Handler implements InvocationHandler {
        private final S3File dir;

        public S3Handler(final S3File dir) {
            this.dir = dir;
        }

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
            if (method.isDefault()) {
                return invokeDefault(proxy, method, args);
            }

            if (method.getDeclaringClass().equals(Object.class)) {
                if (method.getName().equals("toString")) return toString();
                if (method.getName().equals("equals")) return equals(proxy, args);
                if (method.getName().equals("hashCode")) return hashCode();
            }
            if (method.getDeclaringClass().equals(S3.class)) {
                if (method.getName().equals("get")) return dir;
                if (method.getName().equals("parent")) return dir.getParentFile();
                if (method.getName().equals("files")) return files();
                if (method.getName().equals("file") && hasStringArg(method)) return file(args);
                throw new IllegalStateException("Unknown method " + method);
            }

            final S3File file = dir.getFile(name(method));

            final Class<?> returnType = method.getReturnType();

            if (returnType.isArray()) {
                return returnArray(method);
            }

            if (Stream.class.equals(returnType) && args == null) {
                return returnStream(method);
            }

            if (List.class.equals(returnType) && args == null) {
                return returnList(method);
            }

            if (Set.class.equals(returnType) && args == null) {
                return returnSet(method);
            }

            if (Collection.class.equals(returnType) && args == null) {
                return returnList(method);
            }

            if (S3File.class.equals(returnType) && args == null) {
                return returnFile(method, file);
            }

            if (returnType.isInterface() && args != null && args.length == 1 && args[0] instanceof String) {
                return S3.of(returnType, dir.getFile((String) args[0]));
            }

            if (returnType.isInterface() && args == null) {
                return S3.of(returnType, file);
            }

            throw new UnsupportedOperationException(method.toGenericString());
        }

        private boolean hasStringArg(final Method method) {
            final Class<?>[] types = method.getParameterTypes();
            if (types.length != 1) return false;
            return types[0].equals(String.class);
        }

        private Stream<S3File> files() {
            return dir.files();
        }

        private S3File file(final Object[] args) {
            return file((String) args[0]);
        }

        private S3File file(String name) {
            return dir.getFile(name);
        }

        private String name(final Method method) {
            if (method.isAnnotationPresent(Name.class)) {
                return method.getAnnotation(Name.class).value();
            }
            return method.getName();
        }

        private boolean equals(final Object proxy, final Object[] args) {
            if (args.length != 1) return false;
            if (args[0] == null) return false;
            if (!proxy.getClass().isAssignableFrom(args[0].getClass())) return false;

            final InvocationHandler handler = Proxy.getInvocationHandler(args[0]);
            return equals(handler);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final S3.S3Handler that = (S3.S3Handler) o;
            return dir.equals(that.dir);
        }

        @Override
        public int hashCode() {
            return dir.hashCode();
        }

        private static Object invokeDefault(final Object proxy, final Method method, final Object[] args) throws Throwable {
            final float version = Float.parseFloat(System.getProperty("java.class.version"));
            if (version <= 52) { // Java 8
                final Constructor<MethodHandles.Lookup> constructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class);
                constructor.setAccessible(true);

                final Class<?> clazz = method.getDeclaringClass();
                return constructor.newInstance(clazz)
                        .in(clazz)
                        .unreflectSpecial(method, clazz)
                        .bindTo(proxy)
                        .invokeWithArguments(args);
            } else { // Java 9 and later
                return MethodHandles.lookup()
                        .findSpecial(
                                method.getDeclaringClass(),
                                method.getName(),
                                MethodType.methodType(method.getReturnType(), new Class[0]),
                                method.getDeclaringClass()
                        ).bindTo(proxy)
                        .invokeWithArguments(args);
            }
        }

        private Object returnFile(final Method method, final S3File file) throws FileNotFoundException {
            // They want an exception if the file isn't found
            if (exceptions(method).contains(FileNotFoundException.class) && !file.exists()) {
                throw new FileNotFoundException(file.getAbsoluteName());
            }

            return file;
        }

        public List<Class<?>> exceptions(final Method method) {
            final Class<?>[] exceptionTypes = method.getExceptionTypes();
            return Arrays.asList(exceptionTypes);
        }

        private Object returnArray(final Method method) {
            final Predicate<S3File> filter = getFilter(method);

            final Class<?> arrayType = method.getReturnType().getComponentType();

            if (S3File.class.equals(arrayType)) {

                return stream(method)
                        .filter(filter)
                        .toArray(S3File[]::new);

            } else if (arrayType.isInterface()) {

                // will be an array of type Object[]
                final Object[] src = stream(method)
                        .filter(filter)
                        .map(child -> S3.of(arrayType, child))
                        .toArray();

                // will be an array of the user's interface type
                final Object[] dest = (Object[]) Array.newInstance(arrayType, src.length);

                System.arraycopy(src, 0, dest, 0, src.length);

                return dest;
            }

            throw new UnsupportedOperationException(method.toGenericString());
        }

        private Object returnList(final Method method) {
            final Class<?> listType = (Class<?>) Generics.getReturnType(method);
            final Predicate<S3File> filter = getFilter(method);

            if (S3File.class.equals(listType)) {

                return stream(method)
                        .filter(filter)
                        .collect(Collectors.toList());

            } else if (listType.isInterface()) {

                return stream(method)
                        .filter(filter)
                        .map(child -> S3.of(listType, child))
                        .collect(Collectors.toList());
            }

            throw new UnsupportedOperationException(method.toGenericString());
        }

        private Object returnSet(final Method method) {
            final Class<?> listType = (Class<?>) Generics.getReturnType(method);
            final Predicate<S3File> filter = getFilter(method);

            if (S3File.class.equals(listType)) {

                return stream(method)
                        .filter(filter)
                        .collect(Collectors.toSet());

            } else if (listType.isInterface()) {

                return stream(method)
                        .filter(filter)
                        .map(child -> S3.of(listType, child))
                        .collect(Collectors.toSet());
            }

            throw new UnsupportedOperationException(method.toGenericString());
        }

        private Predicate<S3File> getFilter(final Method method) {
            if (method.isAnnotationPresent(Filter.class)) {
                final Filter filter = method.getAnnotation(Filter.class);
                return asPredicate(filter);
            }

            if (method.isAnnotationPresent(Filters.class)) {
                final Filters filters = method.getAnnotation(Filters.class);
                Predicate<S3File> predicate = file -> true;
                for (final Filter filter : filters.value()) {
                    predicate = predicate.and(asPredicate(filter));
                }
                return predicate;
            }

            return pathname -> true;
        }

        private Predicate<S3File> asPredicate(final Filter filter) {
            if (filter == null) return pathname -> true;

            final Class<? extends Predicate<S3File>> clazz = filter.value();
            try {
                return clazz.newInstance();
            } catch (Exception e) {
                throw new IllegalStateException("Unable to instantiate filter " + clazz, e);
            }
        }

        private Object returnStream(final Method method) {
            final Class returnType = (Class) Generics.getReturnType(method);
            final Predicate<S3File> filter = getFilter(method);

            if (returnType.isInterface()) {
                return stream(method)
                        .filter(filter)
                        .map(child -> S3.of(returnType, child));
            }
            if (S3File.class.equals(returnType)) {
                return stream(method)
                        .filter(filter);
            }
            throw new UnsupportedOperationException(method.toGenericString());
        }

        private Stream<S3File> stream(final Method method) {

            final Walk walk = method.getAnnotation(Walk.class);

            if (walk != null) return walk(walk, dir);

            final ListObjectsRequest request = new ListObjectsRequest();

            if (method.isAnnotationPresent(Prefix.class)) {
                final Prefix prefix = method.getAnnotation(Prefix.class);
                request.setPrefix(dir.getPath().getSearchPrefix() + prefix.value());
            }

            if (method.isAnnotationPresent(Marker.class)) {
                final Marker marker = method.getAnnotation(Marker.class);
                request.setMarker(marker.value());
            }

            if (method.isAnnotationPresent(Delimiter.class)) {
                final Delimiter delimiter = method.getAnnotation(Delimiter.class);
                request.setDelimiter(delimiter.value());
            }

            return dir.files(request);
        }

        private static Stream<S3File> walk(final Walk walk, final S3File dir) {
            return walk(dir, walk.maxDepth(), walk.minDepth());
        }

        private static Stream<S3File> walk(final S3File dir, final int maxDepth, final int minDepth) {
            final Predicate<S3File> min = minDepth <= 0 ? s3File -> true : minDepth(dir, minDepth);

            if (maxDepth != -1) {
                return dir.walk(maxDepth).filter(min);
            } else {
                return dir.walk().filter(min);
            }
        }

        private static Predicate<S3File> minDepth(final S3File dir, final int minDepth) {
            int parentDepth = getDepth(dir);
            return s3File -> {
                final int s3FileDepth = getDepth(s3File);
                final int depth = s3FileDepth - parentDepth;
                return depth >= minDepth;
            };
        }

        private static int getDepth(final S3File dir) {
            int depth = 0;
            S3File f = dir;
            while (f != null) {
                f = f.getParentFile();
                depth++;
            }
            return depth;
        }

        @Override
        public String toString() {
            return dir.getAbsoluteName();
        }
    }
}
