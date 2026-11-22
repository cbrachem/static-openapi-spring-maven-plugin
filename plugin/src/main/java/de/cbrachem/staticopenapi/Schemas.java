package de.cbrachem.staticopenapi;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.MonthDay;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.InputStreamSource;

final class Schemas {

    private static final Set<String> BINARY_TYPES = Set.of(
            "jakarta.servlet.http.Part",
            "javax.servlet.http.Part",
            "org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody");

    private static final Set<String> JSON_NODE_TYPES = Set.of(
            "com.fasterxml.jackson.databind.JsonNode",
            "tools.jackson.databind.JsonNode");

    private static final Map<Class<?>, String> JAVA_TIME_TYPES = Map.of(
            YearMonth.class, "string",
            MonthDay.class, "string",
            Year.class, "integer",
            ZoneId.class, "string",
            ZoneOffset.class, "string",
            Period.class, "string");

    static {
        var mapper = Json31.mapper();
        // With this module Jackson marks non-nullable Kotlin properties as required.
        mapper.registerModule(new KotlinModule.Builder().build());
        mapper.setAnnotationIntrospectors(
                AnnotationIntrospector.pair(
                        mapper.getSerializationConfig().getAnnotationIntrospector(), Jackson3Bridge.introspector()),
                AnnotationIntrospector.pair(
                        mapper.getDeserializationConfig().getAnnotationIntrospector(), Jackson3Bridge.introspector()));
    }

    private Schemas() {}

    static void configure(GeneratorConfig config) {
        // Jackson 3 writes enums with toString(). Jackson 2 writes the name.
        mapper().configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, !config.jackson2Defaults());
        ModelResolver.enumsAsRef = config.enumsAsRef();
        ModelConverters.reset();
        var converters = ModelConverters.getInstance(true);
        converters.addConverter(new KotlinModelConverter());
        converters.addConverter(new NullnessModelConverter(!config.jackson2Defaults()));
        converters.addConverter(new Jackson3Bridge());
        converters.addConverter(DiscriminatorMapping::resolve);
        converters.addConverter(Schemas::scalarValue);
        converters.addConverter(new SchemaNameGuard());
    }

    // A JsonNode holds any JSON value, and Jackson writes some java.time types as one value.
    // Without this, swagger-core describes their getters.
    @SuppressWarnings("rawtypes")
    private static Schema scalarValue(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        var rawType = mapper().constructType(type.getType()).getRawClass();
        if (Stream.<Class<?>>iterate(rawType, Objects::nonNull, Class::getSuperclass)
                .anyMatch(candidate -> JSON_NODE_TYPES.contains(candidate.getName()))) {
            return new JsonSchema();
        }
        if (JAVA_TIME_TYPES.containsKey(rawType)) {
            return new JsonSchema().typesItem(JAVA_TIME_TYPES.get(rawType));
        }
        return chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
    }


    static Schema<?> resolve(Type type, List<Annotation> annotations, Components components) {
        var resolvable = ResolvableType.forType(type);
        var element = elementType(resolvable);

        if (isBinary(element.toClass())) {
            return element == resolvable ? binary() : new JsonSchema().typesItem("array").items(binary());
        }

        var annotatedType = new AnnotatedType(type).resolveAsRef(true).ctxAnnotations(annotations.toArray(Annotation[]::new));
        var resolved = ModelConverters.getInstance(true).resolveAsResolvedSchema(annotatedType);
        if (resolved == null || resolved.schema == null) {
            return new JsonSchema().typesItem("object");
        }

        resolved.referencedSchemas.forEach(components::addSchemas);

        return resolved.schema;
    }

    static ObjectMapper mapper() {
        return Json31.mapper();
    }

    // OpenAPI 3.1: "type: [string, null]", or anyOf with a null type next to a $ref.
    static Schema<?> nullable(Schema<?> schema) {
        if (schema.get$ref() != null) {
            return new JsonSchema().anyOf(List.of(schema, new JsonSchema().typesItem("null")));
        }
        if (schema.getTypes() != null) {
            schema.addType("null");
        }
        return schema;
    }

    static void setDefault(Schema<?> schema, Object value) {
        if (schema.getDefault() == null) {
            schema.setDefault(coerce(value, schema.getTypes() == null ? Set.<String>of() : schema.getTypes()));
        }
    }

    // Spring declares default values as text. The schema default needs the JSON type.
    private static Object coerce(Object value, Set<String> types) {
        if (!(value instanceof String text)) {
            return value;
        }
        try {
            if (types.contains("integer")) {
                return Long.valueOf(text);
            }
            if (types.contains("number")) {
                return new BigDecimal(text);
            }
        } catch (NumberFormatException e) {
            return value;
        }
        return types.contains("boolean") ? Boolean.valueOf(text) : value;
    }

    private static Schema<?> binary() {
        return new JsonSchema().typesItem("string").format("binary");
    }

    static boolean isBinary(Type type) {
        return isBinary(elementType(ResolvableType.forType(type)).toClass());
    }

    static Content content(List<String> mediaTypes, Schema<?> schema) {
        var content = new Content();

        mediaTypes.forEach(mediaType -> content.addMediaType(mediaType, new MediaType().schema(schema)));

        return content;
    }

    // swagger-core uses "*/*" when an annotation declares content without a media type.
    static Content withMediaTypes(Content content, List<String> mediaTypes) {
        var wildcard = content == null ? null : content.remove("*/*");

        if (wildcard != null) {
            mediaTypes.forEach(mediaType -> content.addMediaType(mediaType, wildcard));
        }

        return content;
    }

    // An object schema is a $ref. The model itself is in the context.
    static Optional<Schema<?>> definedModel(Schema<?> schema, ModelConverterContext context) {
        if (schema == null || schema.get$ref() == null) {
            return Optional.ofNullable(schema);
        }
        var name = schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1);
        return Optional.ofNullable(context.getDefinedModels().get(name));
    }

    private static ResolvableType elementType(ResolvableType type) {
        if (type.isArray()) {
            return type.getComponentType();
        }
        return Collection.class.isAssignableFrom(type.toClass()) ? type.asCollection().getGeneric(0) : type;
    }

    private static boolean isBinary(Class<?> type) {
        return InputStreamSource.class.isAssignableFrom(type) || BINARY_TYPES.contains(type.getName());
    }
}
