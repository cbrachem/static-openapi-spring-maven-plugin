package de.cbrachem.staticopenapi.fixture;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class EdgeFixtures {

    private EdgeFixtures() {}

    public record Item(String name) {}

    public record Filter(String text, Integer minAge) {}

    public record Page<T>(List<T> items, long total) {}

    public record Address(String street, String city) {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface ParameterObject {}

    public record Search(
            @Schema(description = "Search text", requiredMode = Schema.RequiredMode.REQUIRED) String text,
            Integer limit,
            Address near) {}

    public record Payload(com.fasterxml.jackson.databind.JsonNode legacy, tools.jackson.databind.node.ObjectNode modern) {}

    public static class Customer {
        public String name;

        @JsonUnwrapped
        public Address address;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({@JsonSubTypes.Type(value = Circle.class, name = "circle"), @JsonSubTypes.Type(value = Square.class, name = "square")})
    public sealed interface Shape permits Circle, Square {}

    public record Circle(double radius) implements Shape {}

    public record Square(double side) implements Shape {}

    @OpenAPIDefinition(info = @Info(title = "Edge API", version = "9"), servers = @Server(url = "https://example.org"))
    @SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer")
    @SecurityScheme(name = "key", type = SecuritySchemeType.APIKEY, paramName = "X-Key", in = io.swagger.v3.oas.annotations.enums.SecuritySchemeIn.HEADER)
    public static class ApiConfig {}

    @RestController
    @RequestMapping(path = {"/a", "/b"}, consumes = "application/xml", produces = "application/xml")
    public static class MappingController {
        @RequestMapping("/any")
        public Item any() {
            return null;
        }

        @RequestMapping(path = "/multi", method = {RequestMethod.GET, RequestMethod.POST})
        public Item multi() {
            return null;
        }

        @GetMapping("/files/{*rest}")
        public Item rest(@PathVariable String rest) {
            return null;
        }

        @PostMapping(path = "/json", consumes = "application/json", produces = "application/json")
        public Item json(@RequestBody Item item) {
            return null;
        }

        @PostMapping("/xml")
        public Item xml(@RequestBody Item item) {
            return null;
        }
    }

    @RestController
    @RequestMapping("/returns")
    public static class ReturnController {
        @GetMapping("/future")
        public CompletableFuture<Item> future() {
            return null;
        }

        @GetMapping("/deferred")
        public DeferredResult<List<Item>> deferred() {
            return null;
        }

        @GetMapping("/mono")
        public Mono<ResponseEntity<Item>> mono() {
            return null;
        }

        @GetMapping("/flux")
        public Flux<Item> flux() {
            return null;
        }

        @GetMapping("/optional")
        public Optional<Item> optional() {
            return Optional.empty();
        }

        @GetMapping("/raw")
        @SuppressWarnings("rawtypes")
        public ResponseEntity raw() {
            return null;
        }

        @GetMapping("/wildcard")
        public ResponseEntity<?> wildcard() {
            return null;
        }

        @GetMapping("/void")
        public ResponseEntity<Void> voidEntity() {
            return null;
        }

        @GetMapping("/resource")
        public Resource resource() {
            return null;
        }

        @GetMapping("/page")
        public Page<Item> page() {
            return null;
        }

        @GetMapping("/shape")
        public Shape shape() {
            return null;
        }

        @GetMapping("/customer")
        public Customer customer() {
            return null;
        }

        @GetMapping("/payload")
        public Payload payload() {
            return null;
        }

        @GetMapping("/json")
        public tools.jackson.databind.JsonNode json() {
            return null;
        }
    }

    @RestController
    @RequestMapping("/params")
    public static class ParamController {
        @GetMapping("/cookie")
        public void cookie(@CookieValue("sid") String sid, @CookieValue(name = "theme", required = false) String theme) {}

        @GetMapping("/model")
        public void model(@ModelAttribute Filter filter) {}

        @GetMapping("/unannotated")
        public void unannotated(Filter filter, int page, Locale locale, Principal principal) {}

        @GetMapping("/maps")
        public void maps(@RequestParam Map<String, String> all, @RequestHeader Map<String, String> headers) {}

        @GetMapping("/hidden")
        public void hidden(
                @Parameter(hidden = true) @RequestParam String secret,
                @AuthenticationPrincipal Filter user,
                @RequestParam String shown) {}

        @GetMapping("/list")
        public void list(@RequestParam List<String> tags) {}

        @GetMapping("/object")
        public void object(@ParameterObject Search search, @RequestParam String extra) {}
    }

    @RestController
    @RequestMapping("/swagger")
    @SecurityRequirement(name = "bearer")
    public static class SwaggerController {
        @GetMapping("/hidden")
        @Operation(hidden = true)
        public void hidden() {}

        @GetMapping("/described")
        @Parameter(name = "q", description = "Query text")
        @Parameter(name = "X-Extra", in = ParameterIn.HEADER, description = "Extra header")
        @ApiResponse(responseCode = "200", description = "Found")
        public Item described(@RequestParam String q) {
            return null;
        }

        @GetMapping("/undescribed")
        @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Item.class)))
        @ApiResponse(responseCode = "404")
        @ApiResponse(responseCode = "499")
        @ApiResponse(responseCode = "5XX")
        @ApiResponse(responseCode = "default")
        public Item undescribed() {
            return null;
        }

        @PostMapping("/body")
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "The item", content = @Content(schema = @Schema(implementation = Item.class)))
        public void body(@RequestBody String raw) {}

        @Deprecated
        @GetMapping("/custom")
        @SecurityRequirement(name = "key")
        @Operation(
                operationId = "customId",
                tags = "custom",
                extensions = @Extension(properties = @ExtensionProperty(name = "audience", value = "internal")))
        public void custom() {}
    }

    public enum Tier { GOLD, SILVER }

    public record Member(String name, Tier tier) {}

    @org.jspecify.annotations.NullMarked
    public record Account(String id, @org.jspecify.annotations.Nullable Tier tier) {}

    public record MemberQuery(String name, Tier tier) {}

    @RestController
    @RequestMapping("/tiers")
    public static class TierController {
        @GetMapping("/customers")
        public List<Member> customers(@ParameterObject MemberQuery query) {
            return List.of();
        }

        @GetMapping("/accounts")
        public List<Account> accounts(@RequestParam Optional<Tier> tier) {
            return List.of();
        }
    }

    public record Schedule(
            java.time.YearMonth month,
            java.time.Year year,
            java.time.MonthDay day,
            java.time.ZoneId zone,
            java.time.ZoneOffset offset,
            java.time.Period period,
            java.time.Duration duration,
            java.time.LocalDate date) {}

    public record ScheduleQuery(java.time.YearMonth from) {}

    @RestController
    @RequestMapping("/time")
    public static class TimeController {
        @GetMapping
        public Schedule schedule(@RequestParam java.time.YearMonth month, @ParameterObject ScheduleQuery query) {
            return null;
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "kind")
    @JsonSubTypes({@JsonSubTypes.Type(Cat.class), @JsonSubTypes.Type(Dog.class)})
    public sealed interface Animal permits Cat, Dog {
        String getKind();
    }

    public record Cat(String name) implements Animal {
        public String getKind() {
            return "Cat";
        }
    }

    @com.fasterxml.jackson.annotation.JsonTypeName("DOG")
    public record Dog(String name) implements Animal {
        public String getKind() {
            return "DOG";
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(@JsonSubTypes.Type(value = Van.class, name = "van"))
    @Schema(discriminatorProperty = "type",
            discriminatorMapping = @io.swagger.v3.oas.annotations.media.DiscriminatorMapping(value = "VAN", schema = Van.class))
    public sealed interface Vehicle permits Van {}

    public record Van(int seats) implements Vehicle {}

    @RestController
    @RequestMapping("/polymorphic")
    public static class PolymorphicController {
        @GetMapping("/animal")
        public Animal animal() {
            return null;
        }

        @GetMapping("/vehicle")
        public Vehicle vehicle() {
            return null;
        }
    }
}
