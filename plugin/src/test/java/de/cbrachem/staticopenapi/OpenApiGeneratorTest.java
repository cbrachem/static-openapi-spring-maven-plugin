package de.cbrachem.staticopenapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import de.cbrachem.staticopenapi.fixture.Fixtures;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenApiGeneratorTest {

    private final OpenAPI openApi = OpenApiGenerator.generate(
            List.of(Fixtures.PetController.class, Fixtures.ViewController.class, Fixtures.Pet.class),
            new GeneratorConfig("Pets", "1.0", null, false));

    @Test
    void resolvesComposedMappingsAndStripsPathPatterns() {
        assertThat(openApi.getPaths().keySet()).containsExactly(
                "/api/v1/pets", "/api/v1/pets/{id}", "/api/v1/pets/{id}/label", "/api/v1/pets/{id}/photo");
        assertThat(openApi.getInfo().getTitle()).isEqualTo("Pets");
    }

    @Test
    void readsParametersWithDefaultsAndOptionals() {
        var list = openApi.getPaths().get("/api/v1/pets").getGet();
        assertThat(list.getSummary()).isEqualTo("List pets");
        assertThat(list.getTags()).containsExactly("pets");
        assertThat(list.getParameters()).extracting(Parameter::getName, Parameter::getIn, Parameter::getRequired)
                .containsExactly(
                        tuple("limit", "query", false),
                        tuple("status", "query", false),
                        tuple("X-Tenant", "header", true));
        assertThat(list.getParameters().get(0).getSchema().getDefault()).isEqualTo(20L);
        assertThat(list.getParameters().get(1).getSchema().getEnum()).hasSize(2);
        assertThat(list.getResponses()).containsOnlyKeys("200", "400");
        assertThat(list.getResponses().get("200").getContent().get("application/json").getSchema().getItems().get$ref())
                .isEqualTo("#/components/schemas/Pet");
    }

    @Test
    void readsMappingsAndParameterAnnotationsFromGenericInterface() {
        var find = openApi.getPaths().get("/api/v1/pets/{id}").getGet();
        assertThat(find.getParameters()).singleElement().extracting(Parameter::getIn).isEqualTo("path");
        assertThat(find.getResponses().get("200").getContent().get("application/json").getSchema().get$ref())
                .isEqualTo("#/components/schemas/Pet");

        var create = openApi.getPaths().get("/api/v1/pets").getPost();
        assertThat(create.getRequestBody().getRequired()).isTrue();
        assertThat(create.getRequestBody().getContent().get("application/json").getSchema().get$ref())
                .isEqualTo("#/components/schemas/Pet");
        assertThat(create.getResponses()).containsOnlyKeys("201");
    }

    @Test
    void readsStatusProducesAndMultipart() {
        var paths = openApi.getPaths();
        assertThat(paths.get("/api/v1/pets/{id}").getDelete().getResponses().get("204").getContent()).isNull();
        assertThat(paths.get("/api/v1/pets/{id}/label").getGet().getResponses().get("200").getContent())
                .containsOnlyKeys("text/plain");

        Schema<?> form = paths.get("/api/v1/pets/{id}/photo").getPost().getRequestBody().getContent().get("multipart/form-data").getSchema();
        assertThat(form.getProperties().get("file")).extracting(Schema::getFormat).isEqualTo("binary");
        assertThat(form.getRequired()).containsExactly("file");
    }

    @Test
    void readsSchemaAnnotationsAndValidation() {
        Schema<?> pet = openApi.getComponents().getSchemas().get("Pet");
        assertThat(pet.getRequired()).containsExactly("name");
        assertThat(pet.getProperties().get("age")).extracting(Schema::getDescription).isEqualTo("Age in years");
        assertThat(openApi.getTags()).singleElement().extracting("description").isEqualTo("Pet operations");
    }

    @Test
    void readsJSpecifyNullness() {
        var jspecify = OpenApiGenerator.generate(
                List.of(Fixtures.OwnerController.class), new GeneratorConfig("Owners", "1.0", null, false));

        assertThat(jspecify.getPaths().get("/owners").getGet().getParameters())
                .extracting(Parameter::getName, Parameter::getRequired)
                .containsExactly(tuple("name", true), tuple("email", false));
        Schema<?> owner = jspecify.getComponents().getSchemas().get("Owner");
        assertThat(owner.getRequired()).containsExactlyInAnyOrder("name", "phones", "age");
        assertThat(owner.getProperties().get("email").getTypes()).containsExactlyInAnyOrder("string", "null");
        assertThat(owner.getProperties().get("name").getTypes()).containsExactly("string");
        Schema<?> favorite = owner.getProperties().get("favorite");
        assertThat(favorite.getAnyOf()).hasSize(2);
        assertThat(favorite.getAnyOf().get(0).get$ref()).isEqualTo("#/components/schemas/Pet");
        assertThat(favorite.getAnyOf().get(1).getTypes()).containsExactly("null");
        assertThat(jspecify.getPaths().get("/owners").getGet().getParameters().get(1).getSchema().getTypes())
                .containsExactlyInAnyOrder("string", "null");
        assertThat(jspecify.getComponents().getSchemas().get("LegacyOwner").getRequired()).isNull();
    }

    @Test
    void appliesJackson3AnnotationsAndDefaults() {
        var jackson3 = OpenApiGenerator.generate(
                List.of(Fixtures.OwnerController.class), new GeneratorConfig("Owners", "1.0", null, false));

        Schema<?> shipment = jackson3.getComponents().getSchemas().get("Shipment");
        assertThat(shipment.getProperties()).containsOnlyKeys("tracking_code", "parcel_count", "priority", "sender");
        assertThat(shipment.getRequired()).containsExactly("parcel_count");
        assertThat(shipment.getProperties().get("priority").getEnum()).extracting(Object::toString).containsExactly("low", "high");
        assertThat(shipment.getProperties().get("sender").get$ref()).isEqualTo("#/components/schemas/Owner");
    }

    @Test
    void appliesJackson2DefaultsOnRequest() {
        var jackson2 = OpenApiGenerator.generate(
                List.of(Fixtures.OwnerController.class), new GeneratorConfig("Owners", "1.0", null, true));

        Schema<?> shipment = jackson2.getComponents().getSchemas().get("Shipment");
        assertThat(shipment.getRequired()).isNull();
        assertThat(shipment.getProperties().get("priority").getEnum()).extracting(Object::toString).containsExactly("LOW", "HIGH");
    }
}
