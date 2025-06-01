package com.example.demo.validator

import com.example.demo.model.CustomerCreateDTO
import com.example.demo.model.CustomerSearchRequest
import com.example.demo.model.CustomerUpdateDTO
import io.konform.validation.Validation
import io.konform.validation.constraints.*
import io.konform.validation.onEach

val customerCreateValidator = Validation {
    CustomerCreateDTO::customerName {
        notBlank()
        maxLength(50)
        pattern("^[a-zA-Z0-9\\s]+$".toRegex()) hint "Customer name can only contain letters, spaces, and periods"
    }
}

val customerCreateListValidator = Validation<List<CustomerCreateDTO>> {
    minItems(1)
    maxItems(100)

    onEach {
        run(customerCreateValidator)
    }
}

val customerUpdateValidator = Validation {
    CustomerUpdateDTO::id {
        minimum(1L)
    }

    CustomerUpdateDTO::customerName ifPresent {
        notBlank()
        maxLength(50)
        pattern("^[a-zA-Z0-9\\s]+$".toRegex()) hint "Customer name can only contain letters, spaces, and periods"
    }
}

val customerUpdateListValidator = Validation<List<CustomerUpdateDTO>> {
    minItems(1)
    maxItems(100)

    onEach {
        run(customerUpdateValidator)
    }
}

val customerSearchRequestValidator = Validation<CustomerSearchRequest> {
    CustomerSearchRequest::page ifPresent {
        minimum(1)
        maximum(1000)
    }

    CustomerSearchRequest::size ifPresent {
        minimum(1)
        maximum(1000)
    }

    CustomerSearchRequest::ids ifPresent {
        minItems(1)
        maxItems(100)
        onEach {
            minimum(1L)
        }
    }

    CustomerSearchRequest::customerName ifPresent {
        notBlank()
        maxLength(50)
        pattern("^[a-zA-Z0-9\\s]+$".toRegex()) hint "Customer name can only contain letters, spaces, and periods"
    }

    CustomerSearchRequest::customerNameContains ifPresent {
        notBlank()
        maxLength(100)
    }
}