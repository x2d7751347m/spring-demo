package com.example.demo.model

import java.math.BigDecimal
import java.time.LocalDateTime

data class BeerDTO(
    val id: Long,
    val beerName: String,
    val beerStyle: String,
    val upc: String,
    val quantityOnHand: Int,
    val price: BigDecimal,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

data class BeerUpdateDTO(
    val id: Long,
    val beerName: String? = null,
    val beerStyle: String? = null,
    val upc: String? = null,
    val quantityOnHand: Int? = null,
    val price: BigDecimal? = null,
)