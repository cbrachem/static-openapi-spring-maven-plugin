# static-openapi-maven-plugin

A Maven plugin that generates `openapi.yaml` or `openapi.json` from compiled Spring MVC controllers.
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
  <artifactId>static-openapi-maven-plugin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
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
| `skip` | `false` | Skips the goal. |

## Build and run the sample

```
mvn install
cat sample/target/openapi/openapi.yaml
```

The sample is a Spring Boot application with one Java controller and one Kotlin controller.

## Supported

- `@RestController`, `@Controller` with `@ResponseBody`, composed annotations, mappings on interfaces and generic base types.
- `@PathVariable`, `@RequestParam`, `@RequestHeader`, `@CookieValue`, `@RequestBody`, `@RequestPart`, `MultipartFile`, `@ModelAttribute`.
- `@ResponseStatus`, `ResponseEntity<T>`, `Optional<T>`, `CompletableFuture<T>`, `Mono<T>`, `Flux<T>`, `Flow<T>`.
- `@OpenAPIDefinition`, `@Operation`, `@Parameter`, `@ApiResponse`, `@Schema`, `@Tag`, `@SecurityScheme`, `@SecurityRequirement`, `@Hidden`.
- Java: JSpecify nullness through the Spring `Nullness` API. A `@Nullable` parameter is not required. A non-null reference property is required, for example in `@NullMarked` code.
- Jackson 3 defaults of Spring Boot 4: a primitive record component or creator parameter is required. Enum values use `toString()`.
- Jackson 3 annotations: `@JsonNaming` with a standard strategy and `@JsonSerialize(as = ...)` from `tools.jackson.databind.annotation`.
- Kotlin: a nullable parameter or a parameter with a default value is not required. `suspend` functions work.
- Kotlin: a property is required when it is non-nullable and its constructor parameter has no default value.
- Kotlin: a constant default value becomes the schema `default`. Constants are numbers, strings, booleans, characters and enum entries.

## Limits

- `@Conditional`, `@Profile` and routes that the application registers in code are not visible.
- `ObjectMapper` configuration is not visible. Only annotations on the classes apply.
- `${...}` placeholders in paths stay as they are.
- The plugin reads Kotlin default values from the bytecode. A computed default, for example `LocalDate.now()`, gives no schema `default`. Set `@Schema(defaultValue = ...)` in that case.
- One schema describes a class for requests and responses. A Kotlin property with a default value is thus optional in responses too.
- A primitive property with a setter is not required, because Jackson keeps its initial value.
- Other annotations from `tools.jackson.databind.annotation` and custom naming strategies are not visible.
- `@Schema(requiredMode = REQUIRED)` on a Kotlin property with a default value has no effect.
- Javadoc and KDoc are not in the bytecode.
- The plugin class loader supplies Spring, swagger and Jackson annotation types. A much newer project version can have attributes that the plugin does not read.
- Java parameter names need `-parameters`. Spring Boot sets this flag. Kotlin names come from the metadata.
