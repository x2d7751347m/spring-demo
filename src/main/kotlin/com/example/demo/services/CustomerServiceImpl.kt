package com.example.demo.services

import com.example.demo.mappers.CustomerMapper
import com.example.demo.model.CustomerCreateDTO
import com.example.demo.model.CustomerDTO
import com.example.demo.model.CustomerSearchRequest
import com.example.demo.model.CustomerUpdateDTO
import com.example.demo.repositories.CustomerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.springframework.stereotype.Service

/**
 * Created by jt, Spring Framework Guru.
 */
@Service
class CustomerServiceImpl(
    private val customerRepository: CustomerRepository,
    private val customerMapper: CustomerMapper,
) : CustomerService {

    override suspend fun getEntities(
        searchRequest: CustomerSearchRequest,
    ): Flow<CustomerDTO> =
        customerRepository.getEntities(
            page = searchRequest.page ?: 1,
            size = searchRequest.size ?: 1000,
            ids = searchRequest.ids,
            customerName = searchRequest.customerName,
            customerNameContains = searchRequest.customerNameContains,
        ).map(customerMapper::customerToCustomerDto)


    override suspend fun saveNewEntities(customerCreateDTOs: List<CustomerCreateDTO>): List<CustomerDTO> =
        customerRepository.insertEntities(customerCreateDTOs.map(customerMapper::customerCreateDtoToCustomer))
            .map(customerMapper::customerToCustomerDto)


    override suspend fun patchEntities(customerUpdateDTOs: List<CustomerUpdateDTO>) {
        return customerRepository.patchEntities(customerUpdateDTOs)
    }

    override suspend fun deleteEntitiesById(ids: List<Long>) {
        customerRepository.deleteEntities(ids)
    }
}
