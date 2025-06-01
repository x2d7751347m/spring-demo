package com.example.demo.controllers

import com.example.demo.model.CustomerCreateDTO
import com.example.demo.model.CustomerDTO
import com.example.demo.model.CustomerSearchRequest
import com.example.demo.model.CustomerUpdateDTO
import com.example.demo.services.CustomerService
import com.example.demo.validator.commons.idListValidator
import com.example.demo.validator.customerCreateListValidator
import com.example.demo.validator.customerSearchRequestValidator
import com.example.demo.validator.customerUpdateListValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactor.mono
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

@RestController
class CustomerController(
    private val customerService: CustomerService,
) {
    companion object {
        const val CUSTOMER_PATH: String = "/api/v2/customer"
        const val CUSTOMER_PATH_ID: String = "$CUSTOMER_PATH/{customerId}"
    }

    @DeleteMapping(CUSTOMER_PATH)
    fun deleteCustomersByIds(@RequestBody ids: List<Long>): Mono<ResponseEntity<Void>> {
        return mono {
            idListValidator(ids).errors.let { errors ->
                if (errors.isNotEmpty()) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Validation failed: ${errors.joinToString(", ")}"
                    )
                }
            }

            customerService.deleteEntitiesById(ids)
            ResponseEntity.noContent().build()
        }
    }

    @PatchMapping(CUSTOMER_PATH)
    fun patchCustomers(
        @RequestBody updateDTOs: List<CustomerUpdateDTO>,
    ): Mono<ResponseEntity<Void>> {
        return mono {
            customerUpdateListValidator(updateDTOs).errors.let { errors ->
                if (errors.isNotEmpty()) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Validation failed: ${errors.joinToString(", ")}"
                    )
                }
            }

            customerService.patchEntities(updateDTOs)
            ResponseEntity.ok().build()
        }
    }

    @PostMapping(CUSTOMER_PATH)
    fun createNewCustomers(@RequestBody createDTOs: List<CustomerCreateDTO>): Mono<ResponseEntity<List<CustomerDTO>>> {
        return mono {
            customerCreateListValidator(createDTOs).errors.let { errors ->
                if (errors.isNotEmpty()) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Validation failed: ${errors.joinToString(", ")}"
                    )
                }
            }

            val savedCustomers = customerService.saveNewEntities(createDTOs)
            ResponseEntity.status(HttpStatus.CREATED).body(savedCustomers)
        }
    }

    @PostMapping("$CUSTOMER_PATH/get")
    suspend fun getCustomers(@RequestBody searchRequestBody: CustomerSearchRequest): Flow<CustomerDTO> {
        customerSearchRequestValidator(searchRequestBody).errors.let { errors ->
            if (errors.isNotEmpty()) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Validation failed: ${errors.joinToString(", ")}"
                )
            }
        }

        return customerService.getEntities(searchRequestBody)
    }
}