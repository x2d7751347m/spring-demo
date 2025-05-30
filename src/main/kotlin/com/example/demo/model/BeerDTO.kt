package com.example.demo.model

import java.math.BigDecimal
import java.time.LocalDateTime

data class BeerDTO(
    val id: Int,
    val beerName: String,
    val beerStyle: String,
    val upc: String,
    val quantityOnHand: Int,
    val price: BigDecimal,
    val createdDate: LocalDateTime,
    val lastModifiedDate: LocalDateTime
)