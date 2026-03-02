/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tomitribe.jaws.s3;

import org.tomitribe.util.reflect.Generics;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;

import java.io.FileNotFoundException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
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

public class S3Handler implements InvocationHandler {
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
            if (method.getName().equals("file")) return dir;
            if (method.getName().equals("parent")) return dir.getParentFile();
            throw new IllegalStateException("Unknown method " + method);
        }
        if (method.getDeclaringClass().equals(S3.Dir.class)) {
            if (method.getName().equals("file") && hasStringArg(method)) return file(args);
            if (method.getName().equals("files")) return files();
            if (method.getName().equals("list")) return list();
            throw new IllegalStateException("Unknown method " + method);
        }

        final S3File file = getFile(method);

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
            return dir.getFile((String) args[0]).as(returnType);
        }

        if (returnType.isInterface() && args == null) {
            return file.as(returnType);
        }

        throw new UnsupportedOperationException(method.toGenericString());
    }

    private S3File getFile(final Method method) {
        final Parent parent = method.getAnnotation(Parent.class);
        if (parent != null) {
            S3File parentFile = dir;
            for (int depth = parent.value(); depth > 0; depth--) {
                if (parentFile.getParentFile() == null) throw new NoParentException(method, parentFile);
                parentFile = parentFile.getParentFile();
            }

            return parentFile;
        }

        return dir.getFile(name(method));
    }

    private boolean hasStringArg(final Method method) {
        final Class<?>[] types = method.getParameterTypes();
        if (types.length != 1) return false;
        return types[0].equals(String.class);
    }

    private Stream<S3File> files() {
        return dir.files();
    }

    private Stream<S3File> list() {
        return dir.list();
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
        final S3Handler that = (S3Handler) o;
        return dir.equals(that.dir);
    }

    @Override
    public int hashCode() {
        return dir.hashCode();
    }

    private static Object invokeDefault(final Object proxy, final Method method, final Object[] args) throws Throwable {
        return MethodHandles.lookup()
                .findSpecial(
                        method.getDeclaringClass(),
                        method.getName(),
                        MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
                        method.getDeclaringClass()
                ).bindTo(proxy)
                .invokeWithArguments(args);
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
                    .map(child -> child.as(arrayType))
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
                    .map(child -> child.as(listType))
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
                    .map(child -> child.as(listType))
                    .collect(Collectors.toSet());
        }

        throw new UnsupportedOperationException(method.toGenericString());
    }

    private Predicate<S3File> getFilter(final Method method) {
        // Interface-level filters apply first, then method-level filters
        final Class<?> elementType = getElementType(method);
        return getAnnotationFilter(elementType).and(getAnnotationFilter(method));
    }

    private Predicate<S3File> getAnnotationFilter(final AnnotatedElement element) {
        if (element == null) return file -> true;

        if (element.isAnnotationPresent(Filter.class)) {
            final Filter filter = element.getAnnotation(Filter.class);
            return asPredicate(filter);
        }

        if (element.isAnnotationPresent(Filters.class)) {
            final Filters filters = element.getAnnotation(Filters.class);
            Predicate<S3File> predicate = file -> true;
            for (final Filter filter : filters.value()) {
                predicate = predicate.and(asPredicate(filter));
            }
            return predicate;
        }

        return file -> true;
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
                    .map(child -> child.as(returnType));
        }
        if (S3File.class.equals(returnType)) {
            return stream(method)
                    .filter(filter);
        }
        throw new UnsupportedOperationException(method.toGenericString());
    }

    private Class<?> getElementType(final Method method) {
        final Class<?> returnType = method.getReturnType();
        if (returnType.isArray()) {
            return returnType.getComponentType();
        }
        if (Stream.class.equals(returnType) || List.class.equals(returnType)
                || Set.class.equals(returnType) || Collection.class.equals(returnType)) {
            return (Class<?>) Generics.getReturnType(method);
        }
        return null;
    }

    private Stream<S3File> stream(final Method method) {

        final Walk walk = method.getAnnotation(Walk.class);

        if (walk != null) {
            final String delimiter = method.isAnnotationPresent(Delimiter.class)
                    ? method.getAnnotation(Delimiter.class).value()
                    : "/";
            final String prefix;
            if (method.isAnnotationPresent(Prefix.class)) {
                final String searchPrefix = dir.getPath().getSearchPrefix();
                prefix = (searchPrefix != null ? searchPrefix : "") + method.getAnnotation(Prefix.class).value();
            } else {
                prefix = null;
            }
            Stream<S3File> walked = walk(walk, dir, delimiter, prefix);

            final Class<?> elementType = getElementType(method);
            if (elementType != null && S3.Dir.class.isAssignableFrom(elementType)) {
                walked = walked.filter(S3File::isDirectory);
            } else if (elementType != null && S3.File.class.isAssignableFrom(elementType)) {
                walked = walked.filter(S3File::isFile);
            }

            return walked;
        }

        final Class<?> elementType = getElementType(method);
        final boolean hasListAnnotations = method.isAnnotationPresent(Prefix.class)
                || method.isAnnotationPresent(Marker.class)
                || method.isAnnotationPresent(Delimiter.class);

        // Simple case: no listing annotations, just filter by type
        if (!hasListAnnotations) {
            if (elementType != null && S3.Dir.class.isAssignableFrom(elementType)) {
                return dir.list().filter(S3File::isDirectory);
            }
            if (elementType != null && S3.File.class.isAssignableFrom(elementType)) {
                return dir.list().filter(S3File::isFile);
            }
        }

        ListObjectsRequest.Builder builder = ListObjectsRequest.builder();

        if (method.isAnnotationPresent(Prefix.class)) {
            final Prefix prefix = method.getAnnotation(Prefix.class);
            builder.prefix(dir.getPath().getSearchPrefix() + prefix.value());
        }

        if (method.isAnnotationPresent(Marker.class)) {
            final Marker marker = method.getAnnotation(Marker.class);
            final String searchPrefix = dir.getPath().getSearchPrefix();
            builder.marker((searchPrefix != null ? searchPrefix : "") + marker.value());
        }

        if (method.isAnnotationPresent(Delimiter.class)) {
            final Delimiter delimiter = method.getAnnotation(Delimiter.class);
            builder.delimiter(delimiter.value());
        }

        final ListObjectsRequest request = builder.build();

        // S3.Dir subtypes need delimiter-based listing to discover directories
        if (elementType != null && S3.Dir.class.isAssignableFrom(elementType)) {
            return dir.list(request).filter(S3File::isDirectory);
        }

        Stream<S3File> result = dir.files(request);

        if (elementType != null && S3.File.class.isAssignableFrom(elementType)) {
            result = result.filter(S3File::isFile);
        }

        return result;
    }

    private static Stream<S3File> walk(final Walk walk, final S3File dir, final String delimiter, final String prefix) {
        return walk(dir, walk.maxDepth(), walk.minDepth(), delimiter, prefix);
    }

    private static Stream<S3File> walk(final S3File dir, final int maxDepth, final int minDepth, final String delimiter, final String prefix) {
        final Predicate<S3File> min = minDepth <= 0 ? s3File -> true : minDepth(dir, minDepth);

        final ListObjectsRequest.Builder builder = ListObjectsRequest.builder();
        if (prefix != null) {
            builder.prefix(prefix);
        }

        if (maxDepth != -1) {
            return dir.walk(builder.build(), maxDepth, delimiter).filter(min);
        } else {
            return dir.walk(builder.build(), delimiter).filter(min);
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
