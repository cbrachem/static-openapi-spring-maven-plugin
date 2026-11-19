# static-openapi-maven-plugin

A Maven plugin that generates `openapi.yaml` or `openapi.json` from compiled Spring MVC controllers.
The plugin does not start the application and does not create a Spring context.

## How it works

1. The plugin runs in the `process-classes` phase.
2. ClassGraph finds the classes in `target/classes`.
3. Spring `AnnotatedElementUtils` resolves the mapping annotations. Meta-annotations and `@AliasFor` work.
4. swagger-core `ModelConverters` builds the schemas. Jackson, Bean Validation and `io.swagger.v3.oas.annotations` apply.
5. kotlin-reflect reads the Kotlin metadata. Java and Kotlin classes use the same code path.

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
- Kotlin: a nullable parameter or a parameter with a default value is not required. A non-nullable property is required. `suspend` functions work.

## Limits

- `@Conditional`, `@Profile` and routes that the application registers in code are not visible.
- `ObjectMapper` configuration is not visible. Only annotations on the classes apply.
- `${...}` placeholders in paths stay as they are.
- A Kotlin default value is not in the metadata. The parameter becomes optional, but the schema has no `default`.
- Javadoc and KDoc are not in the bytecode.
- The plugin class loader supplies Spring, swagger and Jackson annotation types. A much newer project version can have attributes that the plugin does not read.
- Java parameter names need `-parameters`. Spring Boot sets this flag. Kotlin names come from the metadata.
