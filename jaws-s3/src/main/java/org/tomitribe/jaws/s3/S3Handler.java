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
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class S3Handler implements InvocationHandler {
    private final S3File dir;
    private final Map<Method, MethodAnnotations> methodAnnotations;

    public S3Handler(final S3File dir, final Class<?> iface) {
        this.dir = dir;
        this.methodAnnotations = buildMethodAnnotations(iface);
    }

    private static Map<Method, MethodAnnotations> buildMethodAnnotations(final Class<?> iface) {
        final Map<Method, MethodAnnotations> map = new HashMap<>();
        for (final Method method : iface.getMethods()) {
            if (method.getDeclaringClass() == Object.class) continue;
            if (method.isDefault()) continue;
            if (Modifier.isStatic(method.getModifiers())) continue;
            map.put(method, MethodAnnotations.of(method));
        }
        return Collections.unmodifiableMap(map);
    }

    private AnnotatedElement element(final Method method, final Object[] args) {
        final MethodAnnotations ma = methodAnnotations.get(method);
        return ma != null ? ma.asAnnotatedElement(args) : method;
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
            return returnArray(method, args);
        }

        if (Stream.class.equals(returnType)) {
            return returnStream(method, args);
        }

        if (List.class.equals(returnType)) {
            return returnList(method, args);
        }

        if (Set.class.equals(returnType)) {
            return returnSet(method, args);
        }

        if (Collection.class.equals(returnType)) {
            return returnList(method, args);
        }

        if (S3File.class.equals(returnType) && args == null) {
            return returnFile(method, file);
        }

        if (returnType.isInterface() && args != null && args.length == 1 && args[0] instanceof String) {
            final String name = (String) args[0];
            final S3File target = dir.getFile(name);
            final Validation validation = Validation.builder()
                    .type(returnType)
                    .element(method)
                    .build();
            if (!validation.test(target)) {
                throw new IllegalArgumentException(
                        String.format("\"%s\" does not match the naming constraints of %s",
                                name, returnType.getSimpleName()));
            }
            return target.as(returnType);
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

    private Object returnArray(final Method method, final Object[] args) {
        final AnnotatedElement element = element(method, args);
        final Predicate<S3File> filter = Validation.builder()
                .type(getElementType(method))
                .element(element)
                .prefix(false)
                .build();

        final Class<?> arrayType = method.getReturnType().getComponentType();

        if (S3File.class.equals(arrayType)) {

            return stream(method, element)
                    .filter(filter)
                    .toArray(S3File[]::new);

        } else if (arrayType.isInterface()) {

            // will be an array of type Object[]
            final Object[] src = stream(method, element)
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

    private Object returnList(final Method method, final Object[] args) {
        final AnnotatedElement element = element(method, args);
        final Class<?> listType = (Class<?>) Generics.getReturnType(method);
        final Predicate<S3File> filter = Validation.builder()
                .type(getElementType(method))
                .element(element)
                .prefix(false)
                .build();

        if (S3File.class.equals(listType)) {

            return stream(method, element)
                    .filter(filter)
                    .collect(Collectors.toList());

        } else if (listType.isInterface()) {

            return stream(method, element)
                    .filter(filter)
                    .map(child -> child.as(listType))
                    .collect(Collectors.toList());
        }

        throw new UnsupportedOperationException(method.toGenericString());
    }

    private Object returnSet(final Method method, final Object[] args) {
        final AnnotatedElement element = element(method, args);
        final Class<?> listType = (Class<?>) Generics.getReturnType(method);
        final Predicate<S3File> filter = Validation.builder()
                .type(getElementType(method))
                .element(element)
                .prefix(false)
                .build();

        if (S3File.class.equals(listType)) {

            return stream(method, element)
                    .filter(filter)
                    .collect(Collectors.toSet());

        } else if (listType.isInterface()) {

            return stream(method, element)
                    .filter(filter)
                    .map(child -> child.as(listType))
                    .collect(Collectors.toSet());
        }

        throw new UnsupportedOperationException(method.toGenericString());
    }

    private Object returnStream(final Method method, final Object[] args) {
        final AnnotatedElement element = element(method, args);
        final Class returnType = (Class) Generics.getReturnType(method);
        final Predicate<S3File> filter = Validation.builder()
                .type(getElementType(method))
                .element(element)
                .prefix(false)
                .build();

        if (returnType.isInterface()) {
            return stream(method, element)
                    .filter(filter)
                    .map(child -> child.as(returnType));
        }
        if (S3File.class.equals(returnType)) {
            return stream(method, element)
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

    private Stream<S3File> stream(final Method method, final AnnotatedElement element) {

        final Class<?> elementType = getElementType(method);
        final boolean recursive = method.isAnnotationPresent(Recursive.class);

        final boolean hasListAnnotations = element.isAnnotationPresent(Prefix.class)
                || method.isAnnotationPresent(Marker.class)
                || method.isAnnotationPresent(Delimiter.class);

        // Simple case: no listing annotations, just filter by type
        if (!hasListAnnotations && !recursive) {
            if (elementType != null && S3.Dir.class.isAssignableFrom(elementType)) {
                return dir.list().filter(S3File::isDirectory);
            }
            if (elementType != null && S3.File.class.isAssignableFrom(elementType)) {
                return dir.list().filter(S3File::isFile);
            }
            // Plain T (not S3.Dir, not S3.File): default is immediate, files only
            return dir.list().filter(S3File::isFile);
        }

        ListObjectsRequest.Builder builder = ListObjectsRequest.builder();

        if (element.isAnnotationPresent(Prefix.class)) {
            final Prefix prefix = element.getAnnotation(Prefix.class);
            final String searchPrefix = dir.getPath().getSearchPrefix();
            builder.prefix((searchPrefix != null ? searchPrefix : "") + prefix.value());
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

        if (recursive) {
            if (elementType != null && S3.Dir.class.isAssignableFrom(elementType)) {
                final String delimiter = method.isAnnotationPresent(Delimiter.class)
                        ? method.getAnnotation(Delimiter.class).value()
                        : "/";
                return dir.walk(request, delimiter).filter(S3File::isDirectory);
            }

            Stream<S3File> result = dir.files(request);
            if (elementType != null && S3.File.class.isAssignableFrom(elementType)) {
                result = result.filter(S3File::isFile);
            }
            return result;
        }

        // Non-recursive with listing annotations: immediate listing
        if (elementType != null && S3.Dir.class.isAssignableFrom(elementType)) {
            return dir.list(request).filter(S3File::isDirectory);
        }

        return dir.list(request).filter(S3File::isFile);
    }

    @Override
    public String toString() {
        return dir.getAbsoluteName();
    }

}
