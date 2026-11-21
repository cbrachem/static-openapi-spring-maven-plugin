package de.cbrachem.staticopenapi;

import io.swagger.v3.core.util.ParameterProcessor;
import io.swagger.v3.jaxrs2.OperationParser;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.lang.reflect.Method;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Stream;
import org.springframework.beans.BeanUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ValueConstants;

final class ParameterReader {

  private static final List<String> IGNORED_ANNOTATIONS = List.of(
      "org.springframework.web.bind.annotation.RequestAttribute",
      "org.springframework.web.bind.annotation.SessionAttribute",
      "org.springframework.web.bind.annotation.MatrixVariable",
      "org.springframework.beans.factory.annotation.Value",
      "org.springframework.security.core.annotation.AuthenticationPrincipal");

  private static final Set<String> FRAMEWORK_PACKAGES = Set.of("java.", "javax.", "jakarta.", "kotlin.", "kotlinx.",
      "reactor.", "org.springframework.");

    // Spring resolves these simple types from the request context, not from a query parameter.
    private static final List<Class<?>> CONTEXT_TYPES = List.of(Locale.class, TimeZone.class, ZoneId.class);

    private final GeneratorConfig config;
  private final Components components;

  ParameterReader(GeneratorConfig config, Components components) {
    this.config = config;
    this.components = components;
  }

  void read(Operation operation, Method method, Class<?> controller, Mapping mapping,
      Optional<KotlinSupport.FunctionInfo> kotlin) {
    Schema<?> form = new JsonSchema().typesItem("object");
    HandlerParameter.of(method, controller, kotlin).stream()
        .filter(parameter -> !isHidden(parameter))
        .forEach(parameter -> read(parameter, operation, form, mapping));

    if (form.getProperties() != null && operation.getRequestBody() == null) {
      var mediaTypes = mapping.consumes().isEmpty() ? List.of("multipart/form-data") : mapping.consumes();
      operation.requestBody(new RequestBody().required(true).content(Schemas.content(mediaTypes, form)));
    }

    declaredRequestBody(method).ifPresent(declared -> operation.requestBody(
        merge(operation.getRequestBody(), declared, config.mediaTypesOrDefault(mapping.consumes()))));
    declaredParameters(method).forEach(declared -> describe(operation, declared));
  }

  private void read(HandlerParameter parameter, Operation operation, Schema<?> form, Mapping mapping) {
    var requestBody = parameter.annotation(org.springframework.web.bind.annotation.RequestBody.class);
    var requestPart = parameter.annotation(RequestPart.class);
    var pathVariable = parameter.annotation(PathVariable.class);
    var requestHeader = parameter.annotation(RequestHeader.class);
    var cookieValue = parameter.annotation(CookieValue.class);
    var requestParam = parameter.annotation(RequestParam.class);

    boolean isFile = Schemas.isBinary(parameter.type());
    boolean isMap = Map.class.isAssignableFrom(parameter.rawType());

    if (requestBody.isPresent()) {
      var schema = Schemas.resolve(parameter.type(), parameter.annotations(), components);
      var body = new RequestBody()
          .required(requestBody.get().required() && !parameter.optional())
          .content(Schemas.content(config.mediaTypesOrDefault(mapping.consumes()), schema));

      var declared = parameter.annotation(io.swagger.v3.oas.annotations.parameters.RequestBody.class)
          .flatMap(annotation -> OperationParser.getRequestBody(annotation, null, null, components, null, true));

      operation.requestBody(declared
          .map(it -> merge(body, it, config.mediaTypesOrDefault(mapping.consumes())))
          .orElse(body));
    } else if (requestPart.isPresent()) {
      addFormPart(form, parameter, requestPart.get().name(), requestPart.get().required());
    } else if (isFile) {
      addFormPart(form, parameter,
          requestParam.map(RequestParam::name).orElse(""),
          requestParam.map(RequestParam::required).orElse(true));
    } else if (isMap) {
      return;
    } else if (pathVariable.isPresent()) {
      add(operation, parameter, "path", pathVariable.get().name(), true, ValueConstants.DEFAULT_NONE);
    } else if (requestHeader.isPresent()) {
      var header = requestHeader.get();
      add(operation, parameter, "header", header.name(), header.required(), header.defaultValue());
    } else if (cookieValue.isPresent()) {
      var cookie = cookieValue.get();
      add(operation, parameter, "cookie", cookie.name(), cookie.required(), cookie.defaultValue());
    } else if (requestParam.isPresent()) {
      var param = requestParam.get();
      add(operation, parameter, "query", param.name(), param.required(), param.defaultValue());
    } else if (CONTEXT_TYPES.stream().anyMatch(type -> type.isAssignableFrom(parameter.rawType()))) {
        return;
    } else if (isParameterObject(parameter)) {
      addParameterObject(operation, parameter);
    } else if (BeanUtils.isSimpleProperty(parameter.rawType())) {
      add(operation, parameter, "query", "", false, ValueConstants.DEFAULT_NONE);
    } else if (parameter.annotation(ModelAttribute.class).isPresent() || !isFrameworkType(parameter.rawType())) {
      // OpenAPI serializes an object in the query as one parameter for each property
      // (style=form, explode=true).
      add(operation, parameter, "query", "", false, ValueConstants.DEFAULT_NONE);
    }
  }

  private void add(Operation operation, HandlerParameter parameter, String in, String annotationName,
      boolean annotationRequired, String defaultValue) {
    boolean hasDefault = !ValueConstants.DEFAULT_NONE.equals(defaultValue);
    var base = new Parameter()
        .in(in)
        .name(parameter.name(annotationName))
        .required(in.equals("path") || annotationRequired && !hasDefault && !parameter.optional());
      var result = ParameterProcessor.applyAnnotations(
              base, parameter.type(), parameter.annotations(), components, new String[0], new String[0], null, true);
      if (result == null) {
        return;
      }
      if (result.getSchema() != null) {
          var schema = result.getSchema();
          (hasDefault ? Optional.<Object>of(defaultValue) : parameter.kotlinDefault())
                  .ifPresent(value -> Schemas.setDefault(schema, value));
          result.setSchema(parameter.nullable() && !in.equals("path") ? Schemas.nullable(schema) : schema);
      }
      operation.addParametersItem(result);
  }

  // Like springdoc's @ParameterObject: each property of the object becomes one query parameter.
  private void addParameterObject(Operation operation, HandlerParameter parameter) {
    var scratch = new Components();
    var ref = Schemas.resolve(parameter.type(), parameter.annotations(), scratch).get$ref();
    var modelName = ref == null ? null : ref.substring(ref.lastIndexOf('/') + 1);
    Schema<?> model = modelName == null || scratch.getSchemas() == null ? null : scratch.getSchemas().get(modelName);

    if (model == null || model.getProperties() == null) {
      add(operation, parameter, "query", "", false, ValueConstants.DEFAULT_NONE);
      return;
    }

    scratch.getSchemas().forEach((name, schema) -> {
      if (!name.equals(modelName)) {
        components.addSchemas(name, schema);
      }
    });
    var required = Optional.ofNullable(model.getRequired()).orElse(List.of());
    model.getProperties().forEach((name, schema) -> operation.addParametersItem(new Parameter()
        .in("query")
        .name(name)
        .required(required.contains(name))
        .description(schema.getDescription())
        .schema(schema.description(null))));
  }

  private void addFormPart(Schema<?> form, HandlerParameter parameter, String annotationName, boolean required) {
    var name = parameter.name(annotationName);
    form.addProperty(name, Schemas.resolve(parameter.type(), parameter.annotations(), components));

    if (required && !parameter.optional()) {
      form.addRequiredItem(name);
    }
  }

  private Optional<RequestBody> declaredRequestBody(Method method) {
    var onMethod = AnnotatedElementUtils.findMergedAnnotation(
        method, io.swagger.v3.oas.annotations.parameters.RequestBody.class);

    var inOperation = Optional.ofNullable(
        AnnotatedElementUtils.findMergedAnnotation(method, io.swagger.v3.oas.annotations.Operation.class))
        .map(io.swagger.v3.oas.annotations.Operation::requestBody);

    return Optional.ofNullable(onMethod).or(() -> inOperation)
        .flatMap(annotation -> OperationParser.getRequestBody(annotation, null, null, components, null, true));
  }

  private Stream<io.swagger.v3.oas.annotations.Parameter> declaredParameters(Method method) {
    var onMethod = AnnotatedElementUtils.findMergedRepeatableAnnotations(
        method, io.swagger.v3.oas.annotations.Parameter.class, io.swagger.v3.oas.annotations.Parameters.class);

    var inOperation = Optional.ofNullable(
        AnnotatedElementUtils.findMergedAnnotation(method, io.swagger.v3.oas.annotations.Operation.class))
        .stream()
        .flatMap(annotation -> Stream.of(annotation.parameters()));

    return Stream.concat(inOperation, onMethod.stream());
  }

  // A method-level @Parameter describes a detected parameter with the same name,
  // or adds a new parameter.
  private void describe(Operation operation, io.swagger.v3.oas.annotations.Parameter declared) {
    var detected = Stream.ofNullable(operation.getParameters()).flatMap(List::stream)
        .filter(parameter -> parameter.getName().equals(declared.name()))
        .findFirst();

    if (detected.isEmpty()) {
      Optional.ofNullable(ParameterProcessor.applyAnnotations(
          null, ParameterProcessor.getParameterType(declared), List.of(declared), components,
          new String[0], new String[0], null, true))
          .ifPresent(operation::addParametersItem);
    } else if (declared.hidden()) {
      operation.getParameters().remove(detected.get());
    } else if (!declared.description().isEmpty()) {
      detected.get().description(declared.description());
    }
  }

  private static RequestBody merge(RequestBody detected, RequestBody declared, List<String> mediaTypes) {
    var content = Schemas.withMediaTypes(declared.getContent(), mediaTypes);

    if (detected == null) {
      return declared.content(content);
    }

    return detected
        .description(declared.getDescription())
        .required(Boolean.TRUE.equals(declared.getRequired()) || Boolean.TRUE.equals(detected.getRequired()))
        .content(content == null || content.isEmpty() ? detected.getContent() : content)
        .extensions(declared.getExtensions());
  }

  private static boolean isHidden(HandlerParameter parameter) {
    return parameter.annotation(Hidden.class).isPresent()
        || parameter.annotation(io.swagger.v3.oas.annotations.Parameter.class)
            .map(io.swagger.v3.oas.annotations.Parameter::hidden).orElse(false)
        || IGNORED_ANNOTATIONS.stream().anyMatch(parameter::isAnnotated);
  }

  // Matched by simple name: springdoc's annotation or one that the project declares itself.
  private static boolean isParameterObject(HandlerParameter parameter) {
    return parameter.annotations().stream()
        .anyMatch(annotation -> annotation.annotationType().getSimpleName().equals("ParameterObject"));
  }

  private static boolean isFrameworkType(Class<?> type) {
    return type.isInterface() || FRAMEWORK_PACKAGES.stream().anyMatch(type.getName()::startsWith);
  }
}
