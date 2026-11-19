package de.cbrachem.staticopenapi;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.core.KotlinDetector;
import org.springframework.core.Nullness;

/**
 * Applies the nullness of Java properties, for example from JSpecify. A non-null reference property and a primitive
 * creator parameter are required. A nullable property gets the null type.
 */
final class NullnessModelConverter implements ModelConverter {

    private final boolean requirePrimitiveCreatorParameters;

    NullnessModelConverter(boolean requirePrimitiveCreatorParameters) {
        this.requirePrimitiveCreatorParameters = requirePrimitiveCreatorParameters;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (!chain.hasNext()) {
            return null;
        }
        Schema schema = chain.next().resolve(type, context, chain);
        var javaType = Schemas.mapper().constructType(type.getType());
        if (!KotlinDetector.isKotlinType(javaType.getRawClass())) {
            Schemas.definedModel(schema, context)
                    .filter(model -> model.getProperties() != null)
                    .ifPresent(model -> {
                        var nullness = nullnessByProperty(javaType);
                        Stream.concat(names(nullness, Nullness.NON_NULL), primitiveCreatorParameters(javaType))
                                .filter(model.getProperties()::containsKey)
                                .filter(name -> !List.copyOf(requiredOf(model)).contains(name))
                                .forEach(model::addRequiredItem);
                        @SuppressWarnings("rawtypes")
                        Map<String, Schema> properties = model.getProperties();
                        names(nullness, Nullness.NULLABLE).forEach(name ->
                                properties.computeIfPresent(name, (key, property) -> Schemas.nullable(property)));
                    });
        }
        return schema;
    }

    private static List<String> requiredOf(Schema<?> model) {
        return model.getRequired() == null ? List.of() : model.getRequired();
    }

    private static Map<String, Nullness> nullnessByProperty(JavaType type) {
        return Schemas.mapper().getSerializationConfig().introspect(type).findProperties().stream()
                .collect(Collectors.toMap(BeanPropertyDefinition::getName, NullnessModelConverter::nullness, (first, second) -> first));
    }

    private static Stream<String> names(Map<String, Nullness> nullness, Nullness wanted) {
        return nullness.entrySet().stream().filter(entry -> entry.getValue() == wanted).map(Map.Entry::getKey);
    }

    // Jackson 3 rejects a missing primitive creator parameter (FAIL_ON_NULL_FOR_PRIMITIVES). Jackson 2 uses 0.
    private Stream<String> primitiveCreatorParameters(JavaType type) {
        if (!requirePrimitiveCreatorParameters) {
            return Stream.empty();
        }
        return Schemas.mapper().getDeserializationConfig().introspect(type).findProperties().stream()
                .filter(property -> property.getConstructorParameter() != null)
                .filter(property -> property.getConstructorParameter().getRawType().isPrimitive())
                .map(BeanPropertyDefinition::getName);
    }

    // A primitive is UNSPECIFIED here. The creator parameter rule decides about primitives.
    private static Nullness nullness(BeanPropertyDefinition property) {
        var member = property.getAccessor() == null ? null : property.getAccessor().getMember();
        if (member instanceof Method method && !method.getReturnType().isPrimitive()) {
            return Nullness.forMethodReturnType(method);
        }
        if (member instanceof Field field && !field.getType().isPrimitive()) {
            return Nullness.forField(field);
        }
        return Nullness.UNSPECIFIED;
    }
}
