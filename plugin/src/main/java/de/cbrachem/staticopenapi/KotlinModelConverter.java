package de.cbrachem.staticopenapi;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Applies Kotlin constructor defaults to object schemas: a property with a default value is not required;
 * if the default value is constant, we'll use it for the schema
 */
final class KotlinModelConverter implements ModelConverter {

    @Override
    @SuppressWarnings("rawtypes")
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (!chain.hasNext()) {
            return null;
        }
        Schema schema = chain.next().resolve(type, context, chain);
        var rawType = Json.mapper().constructType(type.getType()).getRawClass();
        Schemas.definedModel(schema, context)
                .filter(model -> model.getProperties() != null)
                .ifPresent(model -> KotlinSupport.describePrimaryConstructor(rawType).stream()
                        .flatMap(constructor -> constructor.parameters().stream())
                        .filter(KotlinSupport.ParameterInfo::hasDefault)
                        .forEach(parameter -> applyDefault(model, parameter)));
        return schema;
    }

    private static void applyDefault(Schema<?> model, KotlinSupport.ParameterInfo parameter) {
        if (model.getRequired() != null) {
            var required = model.getRequired().stream().filter(name -> !name.equals(parameter.name())).toList();
            model.setRequired(required.isEmpty() ? null : required);
        }
        @SuppressWarnings("rawtypes")
        Map<String, Schema> properties = model.getProperties();
        Optional.ofNullable(properties.get(parameter.name()))
                .filter(property -> property.get$ref() == null && property.getDefault() == null)
                .ifPresent(property -> parameter.defaultValue().ifPresent(property::setDefault));
    }
}
