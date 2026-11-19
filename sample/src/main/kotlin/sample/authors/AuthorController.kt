package sample.authors

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import sample.books.Book

@RestController
@RequestMapping("/authors")
@Tag(name = "authors", description = "Kotlin controller")
class AuthorController {

    @GetMapping
    @Operation(summary = "Search authors")
    fun search(
        @RequestParam query: String?,
        @RequestParam limit: Int = 20,
        @RequestParam activeOnly: Boolean,
        @RequestHeader("X-Trace-Id") traceId: String?,
    ): List<Author> = emptyList()

    @GetMapping("/{id}")
    suspend fun find(@PathVariable id: Long): Author? = null

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody author: Author): Author = author

    @GetMapping("/{id}/books")
    fun books(@PathVariable id: Long): List<Book> = emptyList()
}
