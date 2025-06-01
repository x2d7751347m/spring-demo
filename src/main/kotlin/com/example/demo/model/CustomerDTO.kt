package com.example.demo.model

import com.example.demo.repositories.base.BaseIdDTO
import java.time.LocalDateTime

data class CustomerDTO(
    override val id: Long,

    val customerName: String,

    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) : BaseIdDTO

data class CustomerCreateDTO(
    val customerName: String,
)

data class CustomerUpdateDTO(
    override val id: Long,

    val customerName: String? = null,
) : BaseIdDTO

data class CustomerSearchRequest(
    val page: Int? = null,
    val size: Int? = null,
    val ids: List<Long>? = null,
    val customerName: String? = null,
    val customerNameContains: String? = null,
)