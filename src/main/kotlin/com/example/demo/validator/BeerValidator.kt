package com.example.demo.validator

import com.example.demo.model.BeerCreateDTO
import com.example.demo.model.BeerSearchRequest
import com.example.demo.model.BeerUpdateDTO
import io.konform.validation.Validation
import io.konform.validation.constraints.*
import io.konform.validation.onEach
import java.math.BigDecimal

val beerCreateValidator = Validation {
    BeerCreateDTO::beerName {
        notBlank()
        maxLength(50)
    }

    BeerCreateDTO::beerStyle {
        notBlank()
        maxLength(30)
    }

    BeerCreateDTO::upc {
        minLength(12)
        maxLength(12)
        pattern("\\d{12}".toRegex()) hint "UPC must contain only numbers"
    }

    BeerCreateDTO::quantityOnHand {
        minimum(0)
        maximum(1000)
    }

    BeerCreateDTO::price {
        constrain("Price must be positive") { it > BigDecimal.ZERO }
        constrain("Price cannot exceed 1000000000") { it <= BigDecimal("1000000000") }
    }
}

val beerCreateListValidator = Validation<List<BeerCreateDTO>> {
    minItems(1)
    maxItems(100)

    onEach {
        run(beerCreateValidator)
    }
}

val beerUpdateValidator = Validation {
    BeerUpdateDTO::id {
        minimum(1L)
    }

    BeerUpdateDTO::beerName ifPresent {
        notBlank()
        maxLength(50)
    }

    BeerUpdateDTO::beerStyle ifPresent {
        notBlank()
        maxLength(30)
    }

    BeerUpdateDTO::upc ifPresent {
        minLength(12)
        maxLength(12)
        pattern("\\d{12}".toRegex()) hint "UPC must contain only numbers"
    }

    BeerUpdateDTO::quantityOnHand ifPresent {
        minimum(0)
        maximum(1000)
    }

    BeerUpdateDTO::price ifPresent {
        constrain("Price must be positive") { it > BigDecimal.ZERO }
        constrain("Price cannot exceed 1000000000") { it <= BigDecimal("1000000000") }
    }
}

val beerUpdateListValidator = Validation<List<BeerUpdateDTO>> {
    minItems(1)
    maxItems(100)

    onEach {
        run(beerUpdateValidator)
    }
}

val beerSearchRequestValidator = Validation {
    BeerSearchRequest::page ifPresent {
        minimum(1)
        maximum(1000)
    }

    BeerSearchRequest::size ifPresent {
        minimum(1)
        maximum(1000)
    }

    BeerSearchRequest::ids ifPresent {
        minItems(1)
        maxItems(100)
        onEach {
            minimum(1L)
        }
    }

    BeerSearchRequest::beerName ifPresent {
        notBlank()
        maxLength(50)
    }

    BeerSearchRequest::beerNameContains ifPresent {
        notBlank()
        maxLength(50)
    }

    BeerSearchRequest::beerStyle ifPresent {
        notBlank()
        maxLength(30)
    }

    BeerSearchRequest::beerStyleContains ifPresent {
        notBlank()
        maxLength(30)
    }

    BeerSearchRequest::upc ifPresent {
        minLength(12)
        maxLength(12)
        pattern("\\d{12}".toRegex()) hint "UPC must contain only numbers"
    }

    BeerSearchRequest::quantityOnHand ifPresent {
        minimum(0)
        maximum(1000)
    }

    BeerSearchRequest::minPrice ifPresent {
        constrain("Minimum price must be positive") { it > BigDecimal.ZERO }
        constrain("Minimum price cannot exceed 1000000000") { it <= BigDecimal("1000000000") }
    }

    BeerSearchRequest::maxPrice ifPresent {
        constrain("Maximum price must be positive") { it > BigDecimal.ZERO }
        constrain("Maximum price cannot exceed 1000000000") { it <= BigDecimal("1000000000") }
    }

    // Cross-field validation for price range
    constrain("Maximum price must be greater than minimum price") { request ->
        val minPrice = request.minPrice
        val maxPrice = request.maxPrice
        if (minPrice != null && maxPrice != null) {
            maxPrice > minPrice
        } else {
            true
        }
    }
}