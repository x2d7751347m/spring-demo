package com.example.demo.domain

import org.komapper.annotation.*
import java.time.LocalDateTime

data class Customer(
    val id: Long = 0,

    val customerName: String,

    val version: Int = 0,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)

@KomapperEntityDef(Customer::class)
data class CustomerDef(
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