package de.cbrachem.staticopenapi;

import com.fasterxml.jackson.module.kotlin.KotlinModule;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BinarySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.InputStreamSource;

final class Schemas {

    private static final Set<String> BINARY_TYPES = Set.of(
            "jakarta.servlet.http.Part",
            "javax.servlet.http.Part",
            "org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody");

    static {
        // Jackson reads nullability and default values of Kotlin properties with this module.
        Json.mapper().registerModule(new KotlinModule.Builder().build());
    }

    private Schemas() {}

    static Schema<?> resolve(Type type, List<Annotation> annotations, Components components) {
        var resolvable = ResolvableType.forType(type);
        var element = elementType(resolvable);

        if (isBinary(element.toClass())) {
            return element == resolvable ? new BinarySchema() : new ArraySchema().items(new BinarySchema());
        }

        var annotatedType = new AnnotatedType(type).resolveAsRef(true).ctxAnnotations(annotations.toArray(Annotation[]::new));
        var resolved = ModelConverters.getInstance().resolveAsResolvedSchema(annotatedType);
        if (resolved == null || resolved.schema == null) {
            return new ObjectSchema();
        }

        resolved.referencedSchemas.forEach(components::addSchemas);

        return resolved.schema;
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
