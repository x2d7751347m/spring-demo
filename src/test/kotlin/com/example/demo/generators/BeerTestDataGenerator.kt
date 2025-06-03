package com.example.demo.generators

import com.example.demo.model.BeerCreateDTO
import com.example.demo.model.BeerUpdateDTO
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*
import kotlin.random.Random


object BeerTestDataGenerator {
    fun generateRandomBeerCreateDTOs(count: Int, prefix: String = "ControllerTest"): List<BeerCreateDTO> {
        return (1..count).map {
            BeerCreateDTO(
                beerName = "BeerName-$prefix".take(50),
                beerStyle = "Style-$prefix".take(30),
                upc = generateRandomUPC(),
                quantityOnHand = Random.nextInt(1, 1000),
                price = generateRandomPrice()
            )
        }
    }

    fun generateRandomBeerUpdateDTOs(existingIds: List<Long>, updateRatio: Double = 0.7): List<BeerUpdateDTO> {
        return existingIds.shuffled().take((existingIds.size * updateRatio).toInt()).map { id ->
            BeerUpdateDTO(
                id = id,
                beerName = if (Random.nextBoolean()) "Updated-${UUID.randomUUID()}".take(50) else null,
                beerStyle = if (Random.nextBoolean()) "UpdatedStyle-${UUID.randomUUID()}".take(30) else null,
                price = if (Random.nextBoolean()) generateRandomPrice() else null,
                quantityOnHand = if (Random.nextBoolean()) Random.nextInt(1, 1000) else null,
                upc = if (Random.nextBoolean()) generateRandomUPC() else null
            )
        }
    }

    private fun generateRandomUPC(): String {
        return Random.nextLong(100000000000L, 999999999999L).toString()
    }

    private fun generateRandomPrice(): BigDecimal {
        val randomPrice = Random.nextDouble(1.0, 1000000000.0)
        return BigDecimal(randomPrice).setScale(2, RoundingMode.HALF_UP)
    }
}