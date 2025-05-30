package com.example.demo.model

import java.time.LocalDateTime

data class CustomerDTO(
    val id: Long,

    val customerName: String,

    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

data class CustomerUpdateDTO(
    val id: Long,

    val customerName: String? = null,
)