package de.cbrachem.staticopenapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import de.cbrachem.staticopenapi.fixture.Order;
import de.cbrachem.staticopenapi.fixture.OrderController;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.List;
import org.junit.jupiter.api.Test;

class KotlinSupportTest {

    private final OpenAPI openApi = OpenApiGenerator.generate(
            List.of(OrderController.class, Order.class), new GeneratorConfig("Orders", "1.0", null, false));

    @Test
    void readsNullabilityAndConstantDefaultsOfParameters() {
        var search = openApi.getPaths().get("/orders").getGet();
        assertThat(search.getParameters())
                .extracting(Parameter::getName, Parameter::getRequired, parameter -> text(parameter.getSchema().getDefault()))
                .containsExactly(
                        tuple("query", true, null),
                        tuple("filter", false, null),
                        tuple("limit", false, "20"),
                        tuple("offset", false, "0"),
                        tuple("sort", false, "NAME"),
                        tuple("prefix", false, null));
    }

    @Test
    void requiresOnlyNonNullPropertiesWithoutDefault() {
        Schema<?> order = openApi.getComponents().getSchemas().get("Order");
        assertThat(order.getRequired()).containsExactly("id");
        assertThat(order.getProperties().entrySet())
                .extracting(entry -> entry.getKey(), entry -> text(entry.getValue().getDefault()))
                .containsExactlyInAnyOrder(
                        tuple("id", null),
                        tuple("note", null),
                        tuple("quantity", "1"),
                        tuple("express", "true"),
                        tuple("sort", "DATE"),
                        tuple("label", "none"),
                        tuple("weight", "2.5"),
                        tuple("createdOn", null));
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    @Test
    void readsReturnTypeOfSuspendFunction() {
        var create = openApi.getPaths().get("/orders").getPost();
        assertThat(create.getRequestBody().getContent().get("application/json").getSchema().get$ref())
                .isEqualTo("#/components/schemas/Order");
        assertThat(create.getResponses().get("200").getContent().get("application/json").getSchema().get$ref())
                .isEqualTo("#/components/schemas/Order");
    }
}
