package de.cbrachem.staticopenapi;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;

/**
 * Applies Jackson 3 annotations from {@code tools.jackson.databind.annotation}. swagger-core uses Jackson 2
 * and does not see them. The bridge reads them by name, because Jackson 3 is not on the plugin classpath.
 */
final class Jackson3Bridge implements ModelConverter {

    private static final Logger log = LoggerFactory.getLogger(Jackson3Bridge.class);
    private static final String ANNOTATIONS = "tools.jackson.databind.annotation.";

    static AnnotationIntrospector introspector() {
        return new NopAnnotationIntrospector() {
            @Override
            public Object findNamingStrategy(AnnotatedClass type) {
                return attribute(type.getRawType().getAnnotations(), "JsonNaming", "value")
                        .flatMap(Jackson3Bridge::jackson2Strategy)
                        .orElse(null);
            }
        };
    }

    // @JsonSerialize(as = X.class) on a property or a class: the schema describes X.
    @Override
    @SuppressWarnings("rawtypes")
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (!chain.hasNext()) {
            return null;
        }
        var rawType = Json.mapper().constructType(type.getType()).getRawClass();
        var declared = Stream.of(Optional.ofNullable(type.getCtxAnnotations()).orElse(new Annotation[0]), rawType.getAnnotations())
                .flatMap(annotations -> attribute(annotations, "JsonSerialize", "as").stream())
                .filter(target -> target != Void.class && target != rawType)
                .findFirst();
        declared.ifPresent(type::type);
        return chain.next().resolve(type, context, chain);
    }

    private static Optional<Class<?>> attribute(Annotation[] annotations, String annotationName, String attribute) {
        return Arrays.stream(annotations)
                .filter(annotation -> annotation.annotationType().getName().equals(ANNOTATIONS + annotationName))
                .findFirst()
                .map(annotation -> (Class<?>) AnnotationUtils.getValue(annotation, attribute));
    }

    // The standard strategies have the same nested class names in Jackson 2 and Jackson 3.
    private static Optional<Class<?>> jackson2Strategy(Class<?> jackson3Strategy) {
        var match = Arrays.stream(PropertyNamingStrategies.class.getDeclaredClasses())
                .filter(strategy -> strategy.getSimpleName().equals(jackson3Strategy.getSimpleName()))
                .findFirst();
        if (match.isEmpty()) {
            log.warn("Unsupported naming strategy {}. The schema keeps the Java property names.", jackson3Strategy.getName());
        }
        return match;
    }
}
