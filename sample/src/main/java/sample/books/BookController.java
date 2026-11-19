package sample.books;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/books")
@Tag(name = "books", description = "Java controller")
public class BookController {

    @GetMapping
    @Operation(summary = "Search books")
    public List<Book> search(
            @RequestParam Optional<Book.Genre> genre,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") @Max(100) int limit) {
        return List.of();
    }

    @GetMapping("/{id}")
    @ApiResponse(responseCode = "404", description = "Book not found")
    public ResponseEntity<Book> find(@PathVariable long id) {
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearer")
    public Book create(@Valid @RequestBody Book book) {
        return book;
    }

    @PostMapping("/{id}/cover")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadCover(@PathVariable long id, @RequestPart MultipartFile cover) {}

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearer")
    public void delete(@PathVariable long id) {}
}
