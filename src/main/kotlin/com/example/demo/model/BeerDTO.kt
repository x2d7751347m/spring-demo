package com.example.demo.model

import com.example.demo.repositories.base.BaseIdDTO
import java.math.BigDecimal
import java.time.LocalDateTime

data class BeerDTO(
    override val id: Long,
    val beerName: String,
    val beerStyle: String,
    val upc: String,
    val quantityOnHand: Int,
    val price: BigDecimal,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) : BaseIdDTO

data class BeerCreateDTO(
    val beerName: String,
    val beerStyle: String,
    val upc: String,
    val quantityOnHand: Int,
    val price: BigDecimal,
)

data class BeerUpdateDTO(
    override val id: Long, // This ensures it implements BaseUpdateDTO
    val beerName: String? = null,
    val beerStyle: String? = null,
    val upc: String? = null,
    val quantityOnHand: Int? = null,
    val price: BigDecimal? = null,
) : BaseIdDTO

data class BeerSearchRequest(
    val page: Int? = null,
    val size: Int? = null,
    val ids: List<Long>? = null,
    val beerName: String? = null,
    val beerNameContains: String? = null,
    val beerStyle: String? = null,
    val beerStyleContains: String? = null,
    val upc: String? = null,
    val quantityOnHand: Int? = null,
    val minPrice: BigDecimal? = null,
    val maxPrice: BigDecimal? = null,
)