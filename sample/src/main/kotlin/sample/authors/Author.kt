package sample.authors

import io.swagger.v3.oas.annotations.media.Schema

data class Author(
    val id: Long,
    val name: String,
    @field:Schema(description = "Nullable in Kotlin, therefore not required")
    val biography: String? = null,
    val active: Boolean = true,
)
