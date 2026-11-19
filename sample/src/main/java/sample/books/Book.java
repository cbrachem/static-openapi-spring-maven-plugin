package sample.books;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import sample.authors.Author;

public record Book(
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) Long id,
        @NotBlank @Size(max = 200) String title,
        Author author,
        LocalDate publishedOn,
        Genre genre) {

    public enum Genre { FICTION, NON_FICTION, SCIENCE }
}
