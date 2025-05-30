package com.example.demo.mappers

import com.example.demo.domain.Customer
import com.example.demo.model.CustomerDTO
import org.mapstruct.Mapper
import org.mapstruct.ReportingPolicy

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
interface CustomerMapper {
    fun customerDtoToCustomer(dto: CustomerDTO): Customer

    fun customerToCustomerDto(customer: Customer): CustomerDTO
}