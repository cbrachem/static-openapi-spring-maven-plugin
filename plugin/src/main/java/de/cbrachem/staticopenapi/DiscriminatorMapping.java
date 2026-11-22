package de.cbrachem.staticopenapi;

import static java.util.function.Predicate.not;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Maps the Jackson type names to the subtype schemas. Without a mapping, clients
 * expect the schema name as discriminator value.
 */
final class DiscriminatorMapping {

    private DiscriminatorMapping() {}

    @SuppressWarnings("rawtypes")
    static Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (!chain.hasNext()) {
            return null;
        }
        Schema schema = chain.next().resolve(type, context, chain);
        var rawType = Schemas.mapper().constructType(type.getType()).getRawClass();
        var typeInfo = rawType.getAnnotation(JsonTypeInfo.class);
        var subTypes = rawType.getAnnotation(JsonSubTypes.class);
        if (typeInfo == null || typeInfo.use() != JsonTypeInfo.Id.NAME || subTypes == null) {
            return schema;
        }
        Schemas.definedModel(schema, context)
                .map(Schema::getDiscriminator)
                .filter(discriminator -> discriminator.getMapping() == null)
                .ifPresent(discriminator -> Stream.of(subTypes.value())
                        .forEach(subType -> addMapping(discriminator, subType, context)));
        return schema;
    }

    private static void addMapping(Discriminator discriminator, JsonSubTypes.Type subType, ModelConverterContext context) {
        var names = typeNames(subType);
        if (names.isEmpty()) {
            return;
        }
        var resolved = context.resolve(new AnnotatedType(subType.value()).resolveAsRef(true));
        var ref = resolved.get$ref() != null ? resolved.get$ref() : "#/components/schemas/" + resolved.getName();
        names.forEach(name -> discriminator.mapping(name, ref));
    }

    private static List<String> typeNames(JsonSubTypes.Type subType) {
        var declared = Stream.concat(Stream.of(subType.name()), Stream.of(subType.names()))
                .filter(not(String::isEmpty))
                .toList();
        if (!declared.isEmpty()) {
            return declared;
        }
        return Optional.ofNullable(subType.value().getAnnotation(JsonTypeName.class))
                .map(JsonTypeName::value)
                .filter(not(String::isEmpty))
                .stream()
                .toList();
    }
}
