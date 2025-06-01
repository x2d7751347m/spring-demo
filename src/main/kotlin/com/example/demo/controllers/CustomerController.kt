package com.example.demo.controllers

import com.example.demo.model.*
import com.example.demo.services.CustomerService
import com.example.demo.utils.toMonoResponse
import com.example.demo.utils.toMonoStatusOnly
import com.example.demo.validator.commons.idListValidator
import com.example.demo.validator.customerCreateListValidator
import com.example.demo.validator.customerSearchRequestValidator
import com.example.demo.validator.customerUpdateListValidator
import io.konform.validation.ValidationError
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
            validateAndExecute(
                data = ids,
                validator = { idListValidator(it).errors },
                action = {
                    customerService.deleteEntitiesById(it)
                }
            )
        }.flatMap { result ->
            result.toMonoStatusOnly(HttpStatus.NO_CONTENT)
        }
    }

    @PatchMapping(CUSTOMER_PATH)
    fun patchCustomers(
        @RequestBody updateDTOs: List<CustomerUpdateDTO>,
    ): Mono<ResponseEntity<Void>> {
        return mono {
            validateAndExecute(
                data = updateDTOs,
                validator = { customerUpdateListValidator(it).errors },
                action = {
                    customerService.patchEntities(it)
                }
            )
        }.flatMap { result ->
            result.toMonoStatusOnly(HttpStatus.OK)
        }
    }

    @PostMapping(CUSTOMER_PATH)
    fun createNewCustomers(@RequestBody createDTOs: List<CustomerCreateDTO>): Mono<ResponseEntity<ServiceResult<List<CustomerDTO>>>> {
        return mono {
            validateAndExecute(
                data = createDTOs,
                validator = { customerCreateListValidator(it).errors },
                action = {
                    customerService.saveNewEntities(it)
                }
            )
        }.flatMap { result ->
            result.toMonoResponse(HttpStatus.CREATED)
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

    private suspend fun <T, R> validateAndExecute(
        data: T,
        validator: (T) -> List<ValidationError>,
        action: suspend (T) -> R,
    ): ServiceResult<R> {
        val errors = validator(data)
        return if (errors.isNotEmpty()) {
            ServiceResult.error("Validation failed", errors.map { error -> error.message })
        } else {
            ServiceResult.ok(action(data))
        }
    }
}