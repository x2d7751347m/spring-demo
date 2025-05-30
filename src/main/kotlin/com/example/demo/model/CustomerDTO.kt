package com.example.demo.model

import java.time.LocalDateTime

data class CustomerDTO(
    val id: Int,

    val customerName: String,

    val createdDate: LocalDateTime,
    val lastModifiedDate: LocalDateTime
)