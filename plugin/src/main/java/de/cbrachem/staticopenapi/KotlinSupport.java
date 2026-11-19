package de.cbrachem.staticopenapi;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.full.KClasses;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.KotlinDetector;

final class KotlinSupport {

    private static final Logger log = LoggerFactory.getLogger(KotlinSupport.class);

    record ParameterInfo(String name, boolean nullable, boolean hasDefault, Optional<Object> defaultValue) {}

    record FunctionInfo(List<ParameterInfo> parameters, Type returnType) {}

    private KotlinSupport() {}

    static Optional<FunctionInfo> describe(Method method) {
        return describe(method.getDeclaringClass(), () -> Optional.ofNullable(ReflectJvmMapping.getKotlinFunction(method))
                .map(function -> describe(function, method)));
    }

    static Optional<FunctionInfo> describePrimaryConstructor(Class<?> type) {
        return describe(type, () -> Optional.ofNullable(KClasses.getPrimaryConstructor(JvmClassMappingKt.getKotlinClass(type)))
                .flatMap(function -> Optional.ofNullable(ReflectJvmMapping.getJavaConstructor(function))
                        .map(constructor -> describe(function, constructor))));
    }

    private static Optional<FunctionInfo> describe(Class<?> type, java.util.function.Supplier<Optional<FunctionInfo>> reader) {
        if (!KotlinDetector.isKotlinType(type)) {
            return Optional.empty();
        }
        try {
            return reader.get();
        } catch (RuntimeException | Error e) {
            log.warn("Cannot read Kotlin metadata of {}: {}", type, e.toString());
            return Optional.empty();
        }
    }

    private static FunctionInfo describe(KFunction<?> function, Executable executable) {
        var defaults = KotlinDefaults.of(executable);
        var valueParameters = function.getParameters().stream()
                .filter(parameter -> parameter.getKind() == KParameter.Kind.VALUE)
                .toList();
        var parameters = IntStream.range(0, valueParameters.size())
                .mapToObj(index -> {
                    var parameter = valueParameters.get(index);
                    return new ParameterInfo(
                            parameter.getName(),
                            parameter.getType().isMarkedNullable(),
                            parameter.isOptional(),
                            Optional.ofNullable(defaults.get(index)));
                })
                .toList();

        return new FunctionInfo(parameters, ReflectJvmMapping.getJavaType(function.getReturnType()));
    }
}
