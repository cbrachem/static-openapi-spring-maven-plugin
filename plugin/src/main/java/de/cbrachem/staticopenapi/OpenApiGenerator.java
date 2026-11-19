package de.cbrachem.staticopenapi;

import io.swagger.v3.core.util.AnnotationsUtils;
import io.swagger.v3.jaxrs2.SecurityParser;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.info.Info;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Controller;

public final class OpenApiGenerator {

    private OpenApiGenerator() {}

    public static OpenAPI generate(List<Class<?>> classes, GeneratorConfig config) {
        Schemas.configure(config);
        var openApi = definition(classes, config).components(new Components()).paths(new Paths());

        classes.stream()
                .flatMap(type -> AnnotatedElementUtils
                        .findMergedRepeatableAnnotations(type, SecurityScheme.class, SecuritySchemes.class).stream())
                .flatMap(annotation -> SecurityParser.getSecurityScheme(annotation).stream())
                .forEach(pair -> openApi.getComponents().addSecuritySchemes(pair.key, pair.securityScheme));

        var reader = new OperationReader(config, openApi);
        classes.stream().filter(OpenApiGenerator::isController).forEach(reader::read);

        return sorted(openApi);
    }

    private static boolean isController(Class<?> type) {
        return !type.isInterface()
                && !Modifier.isAbstract(type.getModifiers())
                && AnnotatedElementUtils.hasAnnotation(type, Controller.class);
    }

    private static OpenAPI definition(List<Class<?>> classes, GeneratorConfig config) {
        var definition = classes.stream()
                .flatMap(type -> Optional.ofNullable(
                        AnnotatedElementUtils.findMergedAnnotation(type, OpenAPIDefinition.class)).stream())
                .findFirst();

        var openApi = new OpenAPI(SpecVersion.V31).openapi("3.1.0");

        definition.ifPresent(it -> {
            AnnotationsUtils.getInfo(it.info(), true).ifPresent(openApi::info);
            AnnotationsUtils.getServers(it.servers()).ifPresent(openApi::servers);
            AnnotationsUtils.getTags(it.tags(), false).map(ArrayList::new).ifPresent(openApi::tags);
            AnnotationsUtils.getExternalDocumentation(it.externalDocs(), true).ifPresent(openApi::externalDocs);
            SecurityParser.getSecurityRequirements(it.security()).ifPresent(openApi::security);
        });

        var info = Optional.ofNullable(openApi.getInfo()).orElseGet(Info::new);

        return openApi.info(info
                .title(Optional.ofNullable(info.getTitle()).orElse(config.title()))
                .version(Optional.ofNullable(info.getVersion()).orElse(config.version())));
    }

    // A stable order keeps the generated file diff-friendly.
    private static OpenAPI sorted(OpenAPI openApi) {
        var paths = new Paths();
        paths.putAll(new TreeMap<>(openApi.getPaths()));

        var components = openApi.getComponents();
        if (components.getSchemas() != null) {
            components.schemas(new TreeMap<>(components.getSchemas()));
        }

        boolean noComponents = components.getSchemas() == null && components.getSecuritySchemes() == null;

        return openApi.paths(paths).components(noComponents ? null : components);
    }
}
