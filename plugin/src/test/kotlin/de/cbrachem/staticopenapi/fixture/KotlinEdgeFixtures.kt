package de.cbrachem.staticopenapi.fixture

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

const val MAX_LIMIT = 50

@JvmInline
value class OrderId(val value: Long)

interface SearchApi {
    @GetMapping("/kotlin/search")
    fun search(@RequestParam limit: Int = 5, @RequestParam text: String?): List<Order>
}

@RestController
class KotlinEdgeController : SearchApi {

    override fun search(limit: Int, text: String?): List<Order> = emptyList()

    @GetMapping("/kotlin/orders/{id}")
    fun byId(@PathVariable id: OrderId): Order? = null

    @GetMapping("/kotlin/flow")
    fun flow(): Flow<Order> = emptyFlow()

    @GetMapping("/kotlin/defaults")
    fun defaults(
        @RequestParam separator: Char = ',',
        @RequestParam limit: Int = MAX_LIMIT,
        @RequestParam ratio: Double = 0.5,
        @RequestParam negative: Int = -1,
        @RequestParam flag: Boolean? = false,
    ) = Unit
}
