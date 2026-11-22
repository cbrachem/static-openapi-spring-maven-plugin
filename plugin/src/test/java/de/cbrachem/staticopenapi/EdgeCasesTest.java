package de.cbrachem.staticopenapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import de.cbrachem.staticopenapi.fixture.EdgeFixtures;
import de.cbrachem.staticopenapi.fixture.KotlinEdgeController;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class EdgeCasesTest {

    private final OpenAPI openApi = OpenApiGenerator.generate(
            List.of(
                    EdgeFixtures.ApiConfig.class,
                    EdgeFixtures.MappingController.class,
                    EdgeFixtures.ReturnController.class,
                    EdgeFixtures.ParamController.class,
                    EdgeFixtures.SwaggerController.class,
                    KotlinEdgeController.class),
            new GeneratorConfig("fallback", "0", null, false));

    @Test
    void readsDefinitionAndSecuritySchemes() {
        assertThat(openApi.getOpenapi()).isEqualTo("3.1.0");
        assertThat(openApi.getInfo().getTitle()).isEqualTo("Edge API");
        assertThat(openApi.getInfo().getVersion()).isEqualTo("9");
        assertThat(openApi.getServers()).singleElement().extracting("url").isEqualTo("https://example.org");
        assertThat(openApi.getComponents().getSecuritySchemes()).containsOnlyKeys("bearer", "key");
    }

    @Test
    void combinesPathsMethodsAndMediaTypes() {
        assertThat(openApi.getPaths().keySet()).contains("/a/any", "/b/any", "/a/files/{rest}", "/b/multi");
        assertThat(openApi.getPaths().get("/a/any").readOperationsMap()).hasSize(7);
        assertThat(openApi.getPaths().get("/a/multi").readOperationsMap())
                .containsOnlyKeys(PathItem.HttpMethod.GET, PathItem.HttpMethod.POST);

        var xml = operation("/a/xml", PathItem.HttpMethod.POST);
        assertThat(xml.getRequestBody().getContent()).containsOnlyKeys("application/xml");
        assertThat(xml.getResponses().get("200").getContent()).containsOnlyKeys("application/xml");
        var json = operation("/a/json", PathItem.HttpMethod.POST);
        assertThat(json.getRequestBody().getContent()).containsOnlyKeys("application/json");
        assertThat(json.getResponses().get("200").getContent()).containsOnlyKeys("application/json");
    }

    @Test
    void createsUniqueOperationIds() {
        var ids = openApi.getPaths().values().stream()
                .flatMap(item -> item.readOperations().stream())
                .map(Operation::getOperationId)
                .toList();
        assertThat(ids).doesNotHaveDuplicates().contains("any", "any_1", "customId");
    }

    @Test
    void unwrapsReturnTypes() {
        assertThat(responseSchema("/returns/future").get$ref()).endsWith("/Item");
        assertThat(responseSchema("/returns/deferred").getItems().get$ref()).endsWith("/Item");
        assertThat(responseSchema("/returns/mono").get$ref()).endsWith("/Item");
        assertThat(responseSchema("/returns/flux").getItems().get$ref()).endsWith("/Item");
        assertThat(responseSchema("/returns/optional").get$ref()).endsWith("/Item");
        assertThat(responseSchema("/returns/wildcard").get$ref()).isNull();
        assertThat(responseSchema("/returns/resource").getFormat()).isEqualTo("binary");
        assertThat(get("/returns/raw").getResponses().get("200").getContent()).isNull();
        assertThat(get("/returns/void").getResponses().get("200").getContent()).isNull();
    }

    @Test
    void modelsGenericsPolymorphismAndUnwrappedProperties() {
        var schemas = openApi.getComponents().getSchemas();
        assertThat(responseSchema("/returns/page").get$ref()).endsWith("/PageItem");
        assertThat(schemas.get("PageItem").getRequired()).containsExactly("total");

        assertThat(schemas).containsKeys("Shape", "Circle", "Square");
        assertThat(schemas.get("Shape").getDiscriminator().getPropertyName()).isEqualTo("type");
        assertThat(schemas.get("Shape").getDiscriminator().getMapping()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "circle", "#/components/schemas/Circle", "square", "#/components/schemas/Square"));

        Schema<?> customer = schemas.get("Customer");
        assertThat(customer.getProperties()).containsOnlyKeys("name", "street", "city");
    }

    @Test
    void describesJsonNodeAsAnyValue() {
        var schemas = openApi.getComponents().getSchemas();
        assertThat(schemas.get("Payload").getProperties().values())
                .allSatisfy(property -> assertThat(Json31.pretty(property)).isEqualTo("{ }"));
        assertThat(Json31.pretty(responseSchema("/returns/json"))).isEqualTo("{ }");
        assertThat(schemas.keySet()).noneMatch(name -> name.endsWith("Node"));
    }

    @Test
    void readsParameterKinds() {
        assertThat(parameters("/params/cookie")).containsExactly(tuple("sid", "cookie", true), tuple("theme", "cookie", false));
        assertThat(parameters("/params/model")).containsExactly(tuple("filter", "query", false));
        assertThat(get("/params/model").getParameters().get(0).getSchema().get$ref()).endsWith("/Filter");
        assertThat(parameters("/params/unannotated")).containsExactly(tuple("filter", "query", false), tuple("page", "query", false));
        assertThat(get("/params/maps").getParameters()).isNull();
        assertThat(parameters("/params/hidden")).containsExactly(tuple("shown", "query", true));
        assertThat(get("/params/list").getParameters().get(0).getSchema().getTypes()).containsExactly("array");
    }

    @Test
    void expandsParameterObject() {
        assertThat(parameters("/params/object")).containsExactly(
                tuple("text", "query", true), tuple("limit", "query", false), tuple("near", "query", false),
                tuple("extra", "query", true));
        var parameters = get("/params/object").getParameters();
        assertThat(parameters.get(0).getDescription()).isEqualTo("Search text");
        assertThat(parameters.get(0).getSchema().getDescription()).isNull();
        assertThat(parameters.get(2).getSchema().get$ref()).endsWith("/Address");
        assertThat(openApi.getComponents().getSchemas()).containsKey("Address").doesNotContainKey("Search");
    }

    @Test
    void describesSharedEnumAsRefOnlyWhenEnabled() {
        List<Class<?>> classes = List.of(EdgeFixtures.TierController.class);
        var inline = Json31.pretty(OpenApiGenerator.generate(classes, new GeneratorConfig("t", "0", null, false)));
        var withRefs = OpenApiGenerator.generate(
                classes, new GeneratorConfig("t", "0", null, false, List.of(), List.of(), true));

        assertThat(ModelResolver.enumsAsRef).isFalse();
        assertThat(Json31.pretty(OpenApiGenerator.generate(classes, new GeneratorConfig("t", "0", null, false))))
                .isEqualTo(inline)
                .doesNotContain("#/components/schemas/Tier");

        var schemas = withRefs.getComponents().getSchemas();
        assertThat(schemas.get("Tier").getEnum()).extracting(Object::toString).containsExactly("GOLD", "SILVER");
        var refs = Pattern.compile("#/components/schemas/(\\w+)").matcher(Json31.pretty(withRefs)).results()
                .map(match -> match.group(1)).toList();
        assertThat(schemas.keySet()).containsAll(refs);
        assertThat(refs).filteredOn("Tier"::equals).hasSize(4);
        assertThat(withRefs.getPaths().get("/tiers/customers").getGet().getParameters())
                .extracting(Parameter::getName, Parameter::getRequired, parameter -> parameter.getSchema().get$ref())
                .containsExactly(tuple("name", false, null), tuple("tier", false, "#/components/schemas/Tier"));
        assertThat(withRefs.getPaths().get("/tiers/accounts").getGet().getParameters().get(0).getSchema().get$ref())
                .isEqualTo("#/components/schemas/Tier");
    }

    @Test
    void describesJavaTimeValuesAsScalars() {
        var time = OpenApiGenerator.generate(
                List.of(EdgeFixtures.TimeController.class), new GeneratorConfig("t", "0", null, false));

        var schemas = time.getComponents().getSchemas();
        assertThat(schemas).containsOnlyKeys("Schedule");
        Schema<?> schedule = schemas.get("Schedule");
        assertThat(schedule.getProperties().entrySet())
                .extracting(entry -> entry.getKey(), entry -> entry.getValue().getTypes(), entry -> entry.getValue().getFormat())
                .containsExactly(
                        tuple("month", Set.of("string"), null),
                        tuple("year", Set.of("integer"), null),
                        tuple("day", Set.of("string"), null),
                        tuple("zone", Set.of("string"), null),
                        tuple("offset", Set.of("string"), null),
                        tuple("period", Set.of("string"), null),
                        tuple("duration", Set.of("string"), "duration"),
                        tuple("date", Set.of("string"), "date"));
        assertThat(time.getPaths().get("/time").getGet().getParameters())
                .extracting(Parameter::getName, parameter -> parameter.getSchema().getTypes())
                .containsExactly(tuple("month", Set.of("string")), tuple("from", Set.of("string")));
    }

    @Test
    void mapsOnlyDeclaredJacksonTypeNames() {
        var schemas = OpenApiGenerator.generate(
                List.of(EdgeFixtures.PolymorphicController.class), new GeneratorConfig("t", "0", null, false))
                .getComponents().getSchemas();

        assertThat(schemas.get("Animal").getDiscriminator().getMapping())
                .containsExactlyEntriesOf(Map.of("DOG", "#/components/schemas/Dog"));
        assertThat(schemas.get("Vehicle").getDiscriminator().getMapping())
                .containsExactlyEntriesOf(Map.of("VAN", "#/components/schemas/Van"));
    }

    @Test
    void failsOnSchemaNameCollision() {
        assertThatThrownBy(() -> OpenApiGenerator.generate(
                List.of(EdgeFixtures.RangeController.class), new GeneratorConfig("t", "0", null, false)))
                .isInstanceOf(GenerationException.class)
                .hasMessageContaining("\"Range\"")
                .hasMessageContaining("EdgeFixtures$First$Range")
                .hasMessageContaining("EdgeFixtures$Second$Range")
                .hasMessageContaining("RangeController#second");

        var named = OpenApiGenerator.generate(
                List.of(EdgeFixtures.NamedRangeController.class), new GeneratorConfig("t", "0", null, false));
        assertThat(named.getComponents().getSchemas()).containsOnlyKeys("Range", "LongRange");
    }

    @Test
    void filtersPaths() {
        var filtered = OpenApiGenerator.generate(
                List.of(EdgeFixtures.MappingController.class, EdgeFixtures.ParamController.class),
                new GeneratorConfig("t", "0", null, false, List.of("/a/**", "/params/**"), List.of("/a/files/**", "/params/c*"), false));
        assertThat(filtered.getPaths().keySet())
                .isNotEmpty()
                .allMatch(path -> path.startsWith("/a/") || path.startsWith("/params/"))
                .contains("/a/any", "/params/model")
                .doesNotContain("/a/files/{rest}", "/params/cookie", "/b/any");
    }

    @Test
    void appliesSwaggerAnnotations() {
        assertThat(openApi.getPaths()).doesNotContainKey("/swagger/hidden");

        var described = get("/swagger/described");
        assertThat(described.getParameters()).extracting(Parameter::getName, Parameter::getIn, Parameter::getDescription)
                .containsExactly(tuple("q", "query", "Query text"), tuple("X-Extra", "header", "Extra header"));
        assertThat(described.getResponses().get("200").getDescription()).isEqualTo("Found");
        assertThat(described.getResponses().get("200").getContent().get("application/json").getSchema().get$ref()).endsWith("/Item");
        assertThat(described.getSecurity()).singleElement().extracting(Map::keySet).asString().contains("bearer");

        assertThat(get("/swagger/undescribed").getResponses()).extractingFromEntries(Map.Entry::getKey, entry -> entry.getValue().getDescription())
                .containsExactly(
                        tuple("200", "OK"), tuple("404", "Not Found"), tuple("499", "Client Error"),
                        tuple("5XX", "Server Error"), tuple("default", "Default Response"));

        var body = operation("/swagger/body", PathItem.HttpMethod.POST).getRequestBody();
        assertThat(body.getDescription()).isEqualTo("The item");
        assertThat(body.getRequired()).isTrue();
        assertThat(body.getContent().get("application/json").getSchema().get$ref()).endsWith("/Item");

        var custom = get("/swagger/custom");
        assertThat(custom.getTags()).containsExactly("custom");
        assertThat(custom.getDeprecated()).isTrue();
        assertThat(custom.getSecurity()).singleElement().extracting(Map::keySet).asString().contains("key");
        assertThat(custom.getExtensions()).containsEntry("x-audience", "internal");
    }

    @Test
    void readsKotlinEdgeCases() {
        assertThat(get("/kotlin/search").getParameters())
                .extracting(Parameter::getName, Parameter::getRequired, parameter -> text(parameter.getSchema().getDefault()))
                .containsExactly(tuple("limit", false, "5"), tuple("text", false, null));
        assertThat(get("/kotlin/orders/{id}").getParameters())
                .extracting(Parameter::getName, parameter -> parameter.getSchema().getFormat())
                .containsExactly(tuple("id", "int64"));
        assertThat(responseSchema("/kotlin/flow").getItems().get$ref()).endsWith("/Order");
        assertThat(get("/kotlin/defaults").getParameters())
                .extracting(Parameter::getName, parameter -> text(parameter.getSchema().getDefault()))
                .containsExactly(
                        tuple("separator", ","), tuple("limit", "50"), tuple("ratio", "0.5"),
                        tuple("negative", "-1"), tuple("flag", "false"));
        assertThat(get("/kotlin/defaults").getResponses().get("200").getContent()).isNull();
        assertThat(get("/kotlin/search").getParameters().get(1).getSchema().getTypes()).containsExactlyInAnyOrder("string", "null");
        assertThat(get("/kotlin/search").getParameters().get(0).getSchema().getTypes()).containsExactly("integer");
    }

    private Operation get(String path) {
        return operation(path, PathItem.HttpMethod.GET);
    }

    private Operation operation(String path, PathItem.HttpMethod method) {
        return Objects.requireNonNull(openApi.getPaths().get(path), path).readOperationsMap().get(method);
    }

    private Schema<?> responseSchema(String path) {
        return get(path).getResponses().get("200").getContent().get("application/json").getSchema();
    }

    private List<org.assertj.core.groups.Tuple> parameters(String path) {
        return get(path).getParameters().stream()
                .map(parameter -> tuple(parameter.getName(), parameter.getIn(), parameter.getRequired()))
                .toList();
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
