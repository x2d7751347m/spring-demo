package com.example.demo.generators

import com.example.demo.model.CustomerCreateDTO
import com.example.demo.model.CustomerUpdateDTO
import java.util.*
import kotlin.random.Random


object CustomerTestDataGenerator {
    fun generateRandomCustomerCreateDTOs(count: Int, prefix: String = "ControllerTest"): List<CustomerCreateDTO> {
        return (1..count).map {
            CustomerCreateDTO(
                customerName = "$prefix-CustomerName-${UUID.randomUUID()}",
            )
        }
    }

    fun generateRandomCustomerUpdateDTOs(existingIds: List<Long>, updateRatio: Double = 0.7): List<CustomerUpdateDTO> {
        return existingIds.shuffled().take((existingIds.size * updateRatio).toInt()).map { id ->
            CustomerUpdateDTO(
                id = id,
                customerName = if (Random.nextBoolean()) "Updated-${UUID.randomUUID()}" else null,
            )
        }
    }
}