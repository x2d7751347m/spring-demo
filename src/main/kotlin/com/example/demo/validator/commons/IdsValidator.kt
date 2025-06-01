package com.example.demo.validator.commons

import io.konform.validation.Validation
import io.konform.validation.constraints.maxItems
import io.konform.validation.constraints.minItems
import io.konform.validation.constraints.minimum
import io.konform.validation.onEach

val idListValidator = Validation<List<Long>> {
    minItems(1)
    maxItems(100)
    onEach {
        minimum(1L)
    }
}