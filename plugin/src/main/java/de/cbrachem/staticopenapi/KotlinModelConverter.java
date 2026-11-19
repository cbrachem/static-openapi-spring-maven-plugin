package de.cbrachem.staticopenapi;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Applies Kotlin constructor parameters to object schemas: a nullable property
 * gets the null type,
 * a property with a default value is not required, and a constant default value
 * is used as schema default.
 */
final class KotlinModelConverter implements ModelConverter {

  @Override
  @SuppressWarnings("rawtypes")
  public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
    if (!chain.hasNext()) {
      return null;
    }
    Schema schema = chain.next().resolve(type, context, chain);
    var rawType = Schemas.mapper().constructType(type.getType()).getRawClass();
    Schemas.definedModel(schema, context)
        .filter(model -> model.getProperties() != null)
        .ifPresent(model -> KotlinSupport.describePrimaryConstructor(rawType).stream()
            .flatMap(constructor -> constructor.parameters().stream())
            .forEach(parameter -> apply(model, parameter)));
    return schema;
  }

  private static void apply(Schema<?> model, KotlinSupport.ParameterInfo parameter) {
    @SuppressWarnings("rawtypes")
    Map<String, Schema> properties = model.getProperties();
    if (parameter.nullable()) {
      properties.computeIfPresent(parameter.name(), (name, property) -> Schemas.nullable(property));
    }
    if (parameter.hasDefault() && model.getRequired() != null) {
      var required = model.getRequired().stream().filter(name -> !name.equals(parameter.name())).toList();
      model.setRequired(required.isEmpty() ? null : required);
    }
    Optional.ofNullable(properties.get(parameter.name()))
        .ifPresent(property -> parameter.defaultValue().ifPresent(value -> Schemas.setDefault(property, value)));
  }
}
