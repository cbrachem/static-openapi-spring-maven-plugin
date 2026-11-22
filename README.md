# static-openapi-spring-maven-plugin

A Maven plugin that generates an OpenAPI 3.1 document (`openapi.yaml` or `openapi.json`) from compiled Spring MVC controllers.
The plugin does not start the application and does not create a Spring context.

## How it works

1. The plugin runs in the `process-classes` phase.
2. ClassGraph finds the classes in `target/classes`.
3. Spring `AnnotatedElementUtils` resolves the mapping annotations. Meta-annotations and `@AliasFor` work.
4. swagger-core `ModelConverters` builds the schemas. Jackson, Bean Validation and `io.swagger.v3.oas.annotations` apply.
5. kotlin-reflect reads the Kotlin metadata. ASM reads Kotlin default values. Java and Kotlin classes use the same code path.

## Usage

```xml
<plugin>
  <groupId>de.cbrachem</groupId>
  <artifactId>static-openapi-spring-maven-plugin</artifactId>
  <version>0.3.0</version>
  <executions>
    <execution>
      <goals>
        <goal>generate</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

| Parameter | Default | Description |
|---|---|---|
| `outputDirectory` | `${project.build.directory}/openapi` | Target directory. |
| `fileName` | `openapi` | File name without extension. |
| `formats` | `yaml` | `yaml`, `json` or both. |
| `title`, `version` | project name and version | Fallback when no `@OpenAPIDefinition` sets `info`. |
| `basePackages` | empty | When empty, the plugin scans only this module. When set, the plugin scans these packages on the full compile classpath. |
| `defaultMediaType` | `application/json` | Media type when a mapping declares no `consumes` or `produces`. |
| `jackson2Defaults` | `false` | Set to `true` when the application sets `spring.jackson.use-jackson2-defaults=true`. |
| `pathsToMatch` | empty | Ant patterns of the paths to include, for example `/api/**`. When empty, the plugin includes all paths. |
| `pathsToExclude` | empty | Ant patterns of the paths to exclude, for example `/internal/**`. |
| `enumsAsRef` | `false` | Describe each enum class as a component schema with a `$ref`, like `ModelResolver.enumsAsRef` in swagger-core. |
| `skip` | `false` | Skips the goal. |

## Build and run the sample

```
mvn install
cat sample/target/openapi/openapi.yaml
```

The sample is a Spring Boot application with one Java controller and one Kotlin controller.

## Tests

- `plugin/src/test`: unit tests with Java and Kotlin fixtures.
- `plugin/src/it`: a Maven integration test with the `maven-invoker-plugin`. It runs the goal in a multi-module build.
- `sample/src/test`: the generated document must match `expected-openapi.yaml` and must be valid OpenAPI.

`mvn install` runs all tests. After an intended change of the sample output, copy `sample/target/openapi/openapi.yaml` to `sample/src/test/resources/expected-openapi.yaml`.

## Release

1. Push a tag `v<version>`, for example `v0.1.0`.
2. The `Release` workflow sets the version from the tag, signs the artifacts and publishes them to Maven Central.

The workflow needs the repository secrets `CENTRAL_USERNAME`, `CENTRAL_PASSWORD`, `GPG_PRIVATE_KEY` and `GPG_PASSPHRASE`.

## Supported

- `@RestController`, `@Controller` with `@ResponseBody`, composed annotations, mappings on interfaces and generic base types.
- `@PathVariable`, `@RequestParam`, `@RequestHeader`, `@CookieValue`, `@RequestBody`, `@RequestPart`, `MultipartFile`, `@ModelAttribute`.
- `@ParameterObject`: each property of the object becomes one query parameter. The plugin matches the annotation by its simple name. springdoc's `org.springdoc.core.annotations.ParameterObject` works, and so does an annotation that the project declares itself.
- `@ResponseStatus`, `ResponseEntity<T>`, `Optional<T>`, `CompletableFuture<T>`, `Mono<T>`, `Flux<T>`, `Flow<T>`.
- `@OpenAPIDefinition`, `@Operation`, `@Parameter`, `@ApiResponse`, `@Schema`, `@Tag`, `@SecurityScheme`, `@SecurityRequirement`, `@Hidden`.
- A response without `description` gets the HTTP reason phrase, for example `Not Found`. A range like `4XX` gets `Client Error`, and `default` gets `Default Response`.
- Java: JSpecify nullness through the Spring `Nullness` API. A `@Nullable` parameter is not required. A non-null reference property is required, for example in `@NullMarked` code.
- Jackson 3 defaults of Spring Boot 4: a primitive record component or creator parameter is required. Enum values use `toString()`. `jackson2Defaults` reverts both rules.
- `JsonNode` from Jackson 2 or 3 and its subtypes, for example `ObjectNode`, become the empty schema `{}`, so any JSON value is valid.
- `java.time` values that Jackson writes as one value: `YearMonth`, `MonthDay`, `ZoneId`, `ZoneOffset` and `Period` become `type: string`, and `Year` becomes `type: integer`.
- Jackson 3 annotations: `@JsonNaming` with a standard strategy and `@JsonSerialize(as = ...)` from `tools.jackson.databind.annotation`.
- Nullability in the type: a nullable Kotlin or JSpecify property or parameter gets `type: [<type>, "null"]`. A nullable reference gets `anyOf` with `$ref` and the null type.
- Kotlin: a nullable parameter or a parameter with a default value is not required. `suspend` functions work.
- Kotlin: a property is required when it is non-nullable and its constructor parameter has no default value.
- Kotlin: a constant default value becomes the schema `default`. Constants are numbers, strings, booleans, characters and enum entries.

## Limits

- The plugin writes OpenAPI 3.1 only. OpenAPI 3.0 is not available.
- `@Conditional`, `@Profile` and routes that the application registers in code are not visible.
- `ObjectMapper` configuration is not visible. Only annotations on the classes apply.
- `${...}` placeholders in paths stay as they are.
- `@ParameterObject` does not flatten nested objects. A nested object stays one query parameter with a `$ref`.
- The plugin reads Kotlin default values from the bytecode. A computed default, for example `LocalDate.now()`, gives no schema `default`. Set `@Schema(defaultValue = ...)` in that case.
- One schema describes a class for requests and responses. A Kotlin property with a default value is thus optional in responses too.
- A primitive property with a setter is not required, because Jackson keeps its initial value.
- Other annotations from `tools.jackson.databind.annotation` and custom naming strategies are not visible.
- `@Schema(requiredMode = REQUIRED)` on a Kotlin property with a default value has no effect.
- Javadoc and KDoc are not in the bytecode.
- The plugin class loader supplies Spring, swagger and Jackson annotation types. A much newer project version can have attributes that the plugin does not read.
- Java parameter names need `-parameters`. Spring Boot sets this flag. Kotlin names come from the metadata.
