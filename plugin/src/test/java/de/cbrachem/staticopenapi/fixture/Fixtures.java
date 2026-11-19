package de.cbrachem.staticopenapi.fixture;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AliasFor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

public final class Fixtures {

    private Fixtures() {}

    public record Pet(@NotBlank String name, @Schema(description = "Age in years") Integer age, Status status) {}

    public enum Status { AVAILABLE, SOLD }

    @NullMarked
    public record Owner(String name, @Nullable String email, List<String> phones, int age) {}

    public static class LegacyOwner {
        public String getName() {
            return "";
        }
    }

    @NullMarked
    @RestController
    public static class OwnerController {
        @GetMapping("/owners")
        public List<Owner> search(@RequestParam String name, @RequestParam @Nullable String email) {
            return List.of();
        }

        @GetMapping("/legacy-owners")
        public LegacyOwner legacy() {
            return new LegacyOwner();
        }
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @RestController
    @RequestMapping
    public @interface ApiV1 {
        @AliasFor(annotation = RequestMapping.class, attribute = "path")
        String value();
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @RequestMapping(method = RequestMethod.GET, produces = "text/plain")
    public @interface GetText {
        @AliasFor(annotation = RequestMapping.class, attribute = "path")
        String value();
    }

    public interface CrudApi<T> {
        @GetMapping("/{id:[0-9]{1,3}}")
        ResponseEntity<T> find(@PathVariable("id") long id);

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        T create(@RequestBody T body);
    }

    @ApiV1("/api/v1/pets/")
    @Tag(name = "pets", description = "Pet operations")
    public static class PetController implements CrudApi<Pet> {
        @Override
        public ResponseEntity<Pet> find(long id) {
            return null;
        }

        @Override
        public Pet create(Pet body) {
            return null;
        }

        @GetMapping
        @Operation(summary = "List pets", responses = @ApiResponse(responseCode = "400", description = "Bad filter"))
        public List<Pet> list(
                @RequestParam(defaultValue = "20") int limit,
                @RequestParam Optional<Status> status,
                @RequestHeader("X-Tenant") String tenant,
                java.security.Principal principal) {
            return List.of();
        }

        @GetText("/{id}/label")
        public String label(@PathVariable long id) {
            return "";
        }

        @DeleteMapping("/{id}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public void delete(@PathVariable long id) {}

        @PostMapping("/{id}/photo")
        public void upload(@PathVariable long id, @RequestPart MultipartFile file, @RequestPart(required = false) Pet meta) {}

        @Hidden
        @GetMapping("/internal")
        public String internal() {
            return "";
        }
    }

    @Controller
    public static class ViewController {
        @GetMapping("/home")
        public String home() {
            return "home";
        }
    }
}
