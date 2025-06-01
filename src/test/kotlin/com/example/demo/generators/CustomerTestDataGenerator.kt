package com.example.demo.generators

import com.example.demo.model.CustomerCreateDTO
import com.example.demo.model.CustomerUpdateDTO
import kotlin.random.Random


object CustomerTestDataGenerator {

    fun generateRandomCustomerCreateDTOs(count: Int, prefix: String = "ControllerTest"): List<CustomerCreateDTO> {
        val usedNames = mutableSetOf<String>()

        return (1..count).map {
            var customerName: String
            do {
                customerName = generateRandomCustomerName(prefix.take(20))
            } while (!usedNames.add(customerName))

            CustomerCreateDTO(
                customerName = customerName
            )
        }
    }

    fun generateRandomCustomerUpdateDTOs(existingIds: List<Long>, updateRatio: Double = 0.7): List<CustomerUpdateDTO> {
        val usedNames = mutableSetOf<String>()

        return existingIds.shuffled().take((existingIds.size * updateRatio).toInt()).mapIndexed { _, id ->
            val customerName = if (Random.nextBoolean()) {
                var name: String
                do {
                    name = generateRandomCustomerName("Updated")
                } while (!usedNames.add(name))
                name
            } else null

            CustomerUpdateDTO(
                id = id,
                customerName = customerName
            )
        }
    }

    private fun generateRandomCustomerName(prefix: String): String {
        val allowedChars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz "
        val randomSuffix = (1..10).map { allowedChars.random() }.joinToString("")

        return "$prefix $randomSuffix".take(50)
    }
}