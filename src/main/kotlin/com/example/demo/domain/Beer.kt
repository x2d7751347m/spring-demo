package com.example.demo.domain

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import java.math.BigDecimal
import java.time.LocalDateTime

data class Beer(
    @Id
    val id: Int? = null,

    val beerName: String,
    val beerStyle: String,
    val upc: String,
    val quantityOnHand: Int,
    val price: BigDecimal,

    @CreatedDate
    val createdDate: LocalDateTime? = null,
    @LastModifiedDate
    val lastModifiedDate: LocalDateTime? = null
)