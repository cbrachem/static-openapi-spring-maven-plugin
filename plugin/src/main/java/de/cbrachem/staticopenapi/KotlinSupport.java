package de.cbrachem.staticopenapi;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.KotlinDetector;

final class KotlinSupport {

    private static final Logger log = LoggerFactory.getLogger(KotlinSupport.class);

    record ParameterInfo(String name, boolean nullable, boolean hasDefault) {}

    record FunctionInfo(List<ParameterInfo> parameters, Type returnType) {}

    private KotlinSupport() {}

    static Optional<FunctionInfo> describe(Method method) {
        if (!KotlinDetector.isKotlinType(method.getDeclaringClass())) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(ReflectJvmMapping.getKotlinFunction(method)).map(KotlinSupport::describe);
        } catch (RuntimeException | Error e) {
            log.warn("Cannot read Kotlin metadata of {}: {}", method, e.toString());
            return Optional.empty();
        }
    }

    private static FunctionInfo describe(KFunction<?> function) {
        var parameters = function.getParameters().stream()
                .filter(parameter -> parameter.getKind() == KParameter.Kind.VALUE)
                .map(parameter -> new ParameterInfo(
                        parameter.getName(), parameter.getType().isMarkedNullable(), parameter.isOptional()))
                .toList();

        return new FunctionInfo(parameters, ReflectJvmMapping.getJavaType(function.getReturnType()));
    }
}
