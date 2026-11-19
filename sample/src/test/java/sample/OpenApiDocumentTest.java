package sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.parser.OpenAPIV3Parser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The plugin writes the document in the process-classes phase, thus it exists when the tests run.
 * To accept an intended change, copy target/openapi/openapi.yaml to src/test/resources/expected-openapi.yaml.
 */
class OpenApiDocumentTest {

    private static final Path GENERATED = Path.of("target/openapi/openapi.yaml");
    private static final Path EXPECTED = Path.of("src/test/resources/expected-openapi.yaml");

    @Test
    void generatedDocumentMatchesGoldenFile() throws IOException {
        assertThat(Files.readString(GENERATED)).isEqualTo(Files.readString(EXPECTED));
    }

    @Test
    void generatedDocumentIsValidOpenApi() {
        var result = new OpenAPIV3Parser().readLocation(GENERATED.toAbsolutePath().toString(), null, null);

        assertThat(result.getMessages()).isEmpty();
        assertThat(result.getOpenAPI().getPaths()).isNotEmpty();
    }
}
