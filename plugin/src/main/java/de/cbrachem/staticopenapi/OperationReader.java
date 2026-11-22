package de.cbrachem.staticopenapi;

import static java.util.function.Predicate.not;

import io.swagger.v3.core.util.AnnotationsUtils;
import io.swagger.v3.jaxrs2.OperationParser;
import io.swagger.v3.jaxrs2.SecurityParser;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

final class OperationReader {

    private static final Map<Character, String> SERIES = Map.of(
            '1', "Informational", '2', "Successful", '3', "Redirection", '4', "Client Error", '5', "Server Error");

    private static final Logger log = LoggerFactory.getLogger(OperationReader.class);

    private final GeneratorConfig config;
    private final OpenAPI openApi;
    private final ParameterReader parameterReader;
    private final Set<String> operationIds = new HashSet<>();

    OperationReader(GeneratorConfig config, OpenAPI openApi) {
        this.config = config;
        this.openApi = openApi;
        this.parameterReader = new ParameterReader(config, openApi.getComponents());
    }

    void read(Class<?> controller) {
        var typeMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);

        Map<Method, RequestMapping> handlers = MethodIntrospector.selectMethods(
                controller,
                (MethodIntrospector.MetadataLookup<RequestMapping>) method ->
                        AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class));

        handlers.entrySet().stream()
                .filter(handler -> isDocumented(controller, handler.getKey()))
                .sorted(Comparator.comparing(handler -> handler.getKey().toGenericString()))
                .forEach(handler -> {
                    var mapping = Mapping.of(typeMapping, handler.getValue());
                    mapping.paths().stream().filter(config::includesPath).forEach(path -> mapping.methods().forEach(httpMethod -> pathItem(path)
                            .operation(httpMethod, operationOrFail(controller, handler.getKey(), mapping))));
                });
    }

    private PathItem pathItem(String path) {
        return openApi.getPaths().computeIfAbsent(path, key -> new PathItem());
    }

    private Operation operationOrFail(Class<?> controller, Method method, Mapping mapping) {
        try {
            return operation(controller, method, mapping);
        } catch (GenerationException e) {
            throw new GenerationException(e.getMessage() + " Found in " + controller.getName() + "#" + method.getName() + ".");
        }
    }

    private Operation operation(Class<?> controller, Method method, Mapping mapping) {
        var annotation = Optional.ofNullable(
                AnnotatedElementUtils.findMergedAnnotation(method, io.swagger.v3.oas.annotations.Operation.class));

        var kotlin = KotlinSupport.describe(method);

        var returnType = kotlin.map(KotlinSupport.FunctionInfo::returnType)
                .orElseGet(() -> GenericTypeResolver.resolveType(method.getGenericReturnType(), controller));

        var operation = new Operation()
                .operationId(uniqueOperationId(annotation
                        .map(io.swagger.v3.oas.annotations.Operation::operationId)
                        .filter(not(String::isEmpty))
                        .orElse(method.getName()), controller, method))
                .tags(tags(controller, method, annotation))
                .responses(responses(controller, method, returnType, mapping, annotation));

        annotation.ifPresent(it -> {
            operation.summary(emptyToNull(it.summary())).description(emptyToNull(it.description()));
            AnnotationsUtils.getExternalDocumentation(it.externalDocs(), true).ifPresent(operation::externalDocs);
            var extensions = AnnotationsUtils.getExtensions(true, it.extensions());
            operation.extensions(extensions.isEmpty() ? null : extensions);
        });

        boolean deprecated = annotation.map(io.swagger.v3.oas.annotations.Operation::deprecated).orElse(false)
                || Stream.of(method, controller).anyMatch(element -> element.isAnnotationPresent(Deprecated.class));

        operation.deprecated(deprecated ? true : null);
        security(controller, method, annotation).ifPresent(operation::security);
        parameterReader.read(operation, method, controller, mapping, kotlin);

        return operation;
    }

    private ApiResponses responses(Class<?> controller, Method method, java.lang.reflect.Type returnType,
                                   Mapping mapping, Optional<io.swagger.v3.oas.annotations.Operation> annotation) {
        var produces = config.mediaTypesOrDefault(mapping.produces());

        var declared = Stream.concat(
                        annotation.stream().flatMap(it -> Stream.of(it.responses())),
                        Stream.of(method, controller).flatMap(element -> AnnotatedElementUtils
                                .findMergedRepeatableAnnotations(
                                        element,
                                        io.swagger.v3.oas.annotations.responses.ApiResponse.class,
                                        io.swagger.v3.oas.annotations.responses.ApiResponses.class)
                                .stream()))
                .map(OperationReader::withDescription)
                .toArray(io.swagger.v3.oas.annotations.responses.ApiResponse[]::new);

        var responses = OperationParser.getApiResponses(declared, null, null, openApi.getComponents(), null, true, ApiResponses.DEFAULT)
                .orElseGet(ApiResponses::new);
        responses.values().forEach(response -> Schemas.withMediaTypes(response.getContent(), produces));

        var status = Stream.of(method, controller)
                .flatMap(element -> Stream.ofNullable(
                        AnnotatedElementUtils.findMergedAnnotation(element, ResponseStatus.class)))
                .map(ResponseStatus::code)
                .findFirst()
                .orElse(HttpStatus.OK);

        var response = responses.computeIfAbsent(
                String.valueOf(status.value()), code -> new ApiResponse().description(status.getReasonPhrase()));

        if (response.getContent() == null && response.get$ref() == null) {
            ReturnTypes.bodyType(returnType)
                    .map(type -> Schemas.resolve(type, List.of(method.getAnnotations()), openApi.getComponents()))
                    .ifPresent(schema -> response.content(Schemas.content(produces, schema)));
        }

        var sorted = new ApiResponses();
        sorted.putAll(new TreeMap<>(responses));
        return sorted;
    }

    // OpenAPI requires a response description. swagger-core drops a declared response without one.
    private static io.swagger.v3.oas.annotations.responses.ApiResponse withDescription(
            io.swagger.v3.oas.annotations.responses.ApiResponse response) {
        if (!response.description().isEmpty() || !response.ref().isEmpty()) {
            return response;
        }
        var attributes = new HashMap<>(MergedAnnotation.from(response).asMap());
        attributes.put("description", describe(response.responseCode()));
        return MergedAnnotation.of(io.swagger.v3.oas.annotations.responses.ApiResponse.class, attributes).synthesize();
    }

    private static String describe(String responseCode) {
        if (!responseCode.matches("[1-5](XX|\\d\\d)")) {
            return "Default Response";
        }
        var status = responseCode.endsWith("XX") ? null : HttpStatus.resolve(Integer.parseInt(responseCode));
        return status != null ? status.getReasonPhrase() : SERIES.get(responseCode.charAt(0));
    }

    private List<String> tags(Class<?> controller, Method method,
                              Optional<io.swagger.v3.oas.annotations.Operation> annotation) {
        var declared = Stream.of(method, controller)
                .flatMap(element ->
                        AnnotatedElementUtils.findMergedRepeatableAnnotations(element, Tag.class, Tags.class).stream())
                .toArray(Tag[]::new);

        AnnotationsUtils.getTags(declared, false).stream().flatMap(Set::stream)
                .filter(tag -> Stream.ofNullable(openApi.getTags()).flatMap(List::stream)
                        .noneMatch(known -> known.getName().equals(tag.getName())))
                .forEach(openApi::addTagsItem);

        var names = Stream.concat(
                        annotation.stream().flatMap(it -> Stream.of(it.tags())),
                        Stream.of(declared).map(Tag::name))
                .distinct()
                .toList();

        return names.isEmpty() ? List.of(defaultTag(controller)) : names;
    }

    private static Optional<List<io.swagger.v3.oas.models.security.SecurityRequirement>> security(
            Class<?> controller, Method method, Optional<io.swagger.v3.oas.annotations.Operation> annotation) {
        var onOperation = annotation.stream().flatMap(it -> Stream.of(it.security()));

        var declared = Stream.of(method, controller)
                .map(element -> AnnotatedElementUtils.findMergedRepeatableAnnotations(
                        element, SecurityRequirement.class, SecurityRequirements.class))
                .filter(not(Set::isEmpty))
                .findFirst()
                .stream()
                .flatMap(Set::stream);

        return SecurityParser.getSecurityRequirements(
                Stream.concat(onOperation, declared).toArray(SecurityRequirement[]::new));
    }

    private String uniqueOperationId(String base, Class<?> controller, Method method) {
        var unique = Stream.iterate(0, i -> i + 1)
                .map(i -> i == 0 ? base : base + "_" + i)
                .filter(operationIds::add)
                .findFirst()
                .orElseThrow();
        if (!unique.equals(base)) {
            log.warn("The operationId {} is taken. {}#{} renamed to {}. Set @Operation(operationId = ...) to choose a name.",
                    base, controller.getName(), method.getName(), unique);
        }
        return unique;
    }

    private static boolean isDocumented(Class<?> controller, Method method) {
        var operation = AnnotatedElementUtils.findMergedAnnotation(method, io.swagger.v3.oas.annotations.Operation.class);

        return Stream.<AnnotatedElement>of(method, controller)
                        .anyMatch(element -> AnnotatedElementUtils.hasAnnotation(element, ResponseBody.class))
                && Stream.<AnnotatedElement>of(method, controller)
                        .noneMatch(element -> AnnotatedElementUtils.hasAnnotation(element, Hidden.class))
                && (operation == null || !operation.hidden());
    }

    // kebap-case: "PetController" becomes "pet-controller"
    private static String defaultTag(Class<?> controller) {
        return controller.getSimpleName().replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
