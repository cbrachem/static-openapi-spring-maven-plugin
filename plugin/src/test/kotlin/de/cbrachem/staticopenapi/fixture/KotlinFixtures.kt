package de.cbrachem.staticopenapi.fixture

import java.time.LocalDate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

enum class Sort { NAME, DATE }

data class Order(
    val id: Long,
    val note: String?,
    val quantity: Int = 1,
    val express: Boolean = true,
    val sort: Sort = Sort.DATE,
    val label: String = "none",
    val weight: Double? = 2.5,
    val createdOn: LocalDate = LocalDate.now(),
)

@RestController
class OrderController {

    @GetMapping("/orders")
    fun search(
        @RequestParam query: String,
        @RequestParam filter: String?,
        @RequestParam limit: Int = 20,
        @RequestParam offset: Long = 0,
        @RequestParam sort: Sort = Sort.NAME,
        @RequestParam prefix: String = "a".repeat(2),
    ): List<Order> = emptyList()

    @PostMapping("/orders")
    suspend fun create(@RequestBody order: Order): Order = order
}
