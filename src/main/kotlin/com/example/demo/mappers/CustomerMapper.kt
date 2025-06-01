package com.example.demo.mappers

import com.example.demo.domain.Customer
import com.example.demo.model.CustomerCreateDTO
import com.example.demo.model.CustomerDTO
import org.mapstruct.Mapper
import org.mapstruct.ReportingPolicy

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
interface CustomerMapper {
    fun customerCreateDtoToCustomer(customerCreateDTO: CustomerCreateDTO): Customer
    fun customerToCustomerDto(customer: Customer): CustomerDTO
}