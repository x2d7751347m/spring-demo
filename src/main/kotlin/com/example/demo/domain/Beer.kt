package com.example.demo.domain

import org.komapper.annotation.*
import java.math.BigDecimal
import java.time.LocalDateTime

data class Beer(
    val id: Long = 0,

    val beerName: String,
    val beerStyle: String,
    val upc: String,
    val quantityOnHand: Int,
    val price: BigDecimal,

    val version: Int = 0,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)

@KomapperEntityDef(Beer::class)
data class BeerDef(
    @KomapperId
    @KomapperAutoIncrement
    val id: Nothing,
    @KomapperVersion
    val version: Nothing,
    @KomapperCreatedAt
    val createdAt: LocalDateTime,
    @KomapperUpdatedAt
    val updatedAt: LocalDateTime,
)