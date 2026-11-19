package de.cbrachem.staticopenapi;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

record HandlerParameter(Method method, Optional<String> declaredName, Type type, List<Annotation> annotations, boolean optional,
                        Optional<Object> kotlinDefault) {

    static List<HandlerParameter> of(Method method, Class<?> controller, Optional<KotlinSupport.FunctionInfo> kotlin) {
        var parameters = method.getParameters();

        return IntStream.range(0, parameters.length)
                .filter(i -> !parameters[i].getType().getName().equals("kotlin.coroutines.Continuation"))
                .mapToObj(i -> {
                    var kotlinInfo = kotlin.map(info -> info.parameters().get(i));
                    var type = GenericTypeResolver.resolveType(method.getGenericParameterTypes()[i], controller);
                    boolean javaOptional = ResolvableType.forType(type).resolve() == Optional.class;

                    return new HandlerParameter(
                            method,
                            kotlinInfo.map(KotlinSupport.ParameterInfo::name)
                                    .or(() -> Optional.of(parameters[i]).filter(Parameter::isNamePresent).map(Parameter::getName)),
                            javaOptional ? ResolvableType.forType(type).getGeneric(0).getType() : type,
                            inheritedAnnotations(method, controller, i),
                            javaOptional || kotlinInfo.map(info -> info.nullable() || info.hasDefault()).orElse(false),
                            kotlinInfo.flatMap(KotlinSupport.ParameterInfo::defaultValue));
                })
                .toList();
    }

    <A extends Annotation> Optional<A> annotation(Class<A> annotationType) {
        return Optional.ofNullable(AnnotatedElementUtils.findMergedAnnotation(element(), annotationType));
    }

    boolean isAnnotated(String annotationName) {
        return AnnotatedElementUtils.isAnnotated(element(), annotationName);
    }

    Class<?> rawType() {
        return ResolvableType.forType(type).toClass();
    }

    String name(String annotationName) {
        if (!annotationName.isEmpty()) {
            return annotationName;
        }

        return declaredName.orElseThrow(() -> new IllegalStateException(
                "No parameter name for " + method + ". Compile with -parameters or set the name in the annotation."));
    }

    private AnnotatedElement element() {
        return AnnotatedElementUtils.forAnnotations(annotations.toArray(Annotation[]::new));
    }

    // Spring also reads parameter annotations from overridden interface and superclass methods.
    private static List<Annotation> inheritedAnnotations(Method method, Class<?> controller, int index) {
        var superTypes = Stream.concat(
                Stream.<Class<?>>iterate(controller, type -> type != null && type != Object.class, Class::getSuperclass),
                ClassUtils.getAllInterfacesForClassAsSet(controller).stream());

        return Stream.concat(Stream.of(method), superTypes.flatMap(type -> Arrays.stream(type.getDeclaredMethods())))
                .filter(candidate -> isSameSignature(candidate, method, controller))
                .flatMap(candidate -> Arrays.stream(candidate.getParameters()[index].getAnnotations()))
                .filter(distinctByType())
                .toList();
    }

    private static boolean isSameSignature(Method candidate, Method method, Class<?> controller) {
        return candidate.getName().equals(method.getName())
                && candidate.getParameterCount() == method.getParameterCount()
                && IntStream.range(0, method.getParameterCount()).allMatch(i ->
                        ResolvableType.forMethodParameter(candidate, i, controller).toClass() == method.getParameterTypes()[i]);
    }

    private static Predicate<Annotation> distinctByType() {
        var seen = new HashSet<Class<?>>();
        return annotation -> seen.add(annotation.annotationType());
    }
}
