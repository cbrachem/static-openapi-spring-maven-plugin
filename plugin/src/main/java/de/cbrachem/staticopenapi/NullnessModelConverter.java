package de.cbrachem.staticopenapi;

import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import org.springframework.core.KotlinDetector;
import org.springframework.core.Nullness;

/**
 * Marks a property of a Java class as required when its type is non-null, for example in JSpecify
 * {@code @NullMarked} code. Primitive properties stay as they are, because Jackson gives them a default value.
 */
final class NullnessModelConverter implements ModelConverter {

    @Override
    @SuppressWarnings("rawtypes")
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (!chain.hasNext()) {
            return null;
        }
        Schema schema = chain.next().resolve(type, context, chain);
        var javaType = Json.mapper().constructType(type.getType());
        if (!KotlinDetector.isKotlinType(javaType.getRawClass())) {
            Schemas.definedModel(schema, context)
                    .filter(model -> model.getProperties() != null)
                    .ifPresent(model -> Json.mapper().getSerializationConfig().introspect(javaType).findProperties().stream()
                            .filter(NullnessModelConverter::isNonNullReference)
                            .map(BeanPropertyDefinition::getName)
                            .filter(model.getProperties()::containsKey)
                            .filter(name -> !List.copyOf(requiredOf(model)).contains(name))
                            .forEach(model::addRequiredItem));
        }
        return schema;
    }

    private static List<String> requiredOf(Schema<?> model) {
        return model.getRequired() == null ? List.of() : model.getRequired();
    }

    private static boolean isNonNullReference(BeanPropertyDefinition property) {
        var member = property.getAccessor() == null ? null : property.getAccessor().getMember();
        if (member instanceof Method method) {
            return !method.getReturnType().isPrimitive() && Nullness.forMethodReturnType(method) == Nullness.NON_NULL;
        }
        return member instanceof Field field
                && !field.getType().isPrimitive()
                && Nullness.forField(field) == Nullness.NON_NULL;
    }
}
