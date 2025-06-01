package com.example.demo.services

import com.example.demo.model.CustomerCreateDTO
import com.example.demo.model.CustomerDTO
import com.example.demo.model.CustomerSearchRequest
import com.example.demo.model.CustomerUpdateDTO
import kotlinx.coroutines.flow.Flow

interface CustomerService {
    suspend fun getEntities(
        searchRequest: CustomerSearchRequest,
    ): Flow<CustomerDTO>

    suspend fun saveNewEntities(customerCreateDTOs: List<CustomerCreateDTO>): List<CustomerDTO>

    suspend fun patchEntities(customerUpdateDTOs: List<CustomerUpdateDTO>)

    suspend fun deleteEntitiesById(ids: List<Long>)
}
