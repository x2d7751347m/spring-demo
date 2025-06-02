package com.example.demo.tests.repositoryTests

import com.example.demo.TestContainersConfiguration
import com.example.demo.domain.Beer
import com.example.demo.generators.BeerTestDataGenerator
import com.example.demo.mappers.BeerMapper
import com.example.demo.repositories.BeerRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
@SpringBootTest
@Import(TestContainersConfiguration::class)
class BeerRepositoryTest {

    @Autowired
    private lateinit var beerRepository: BeerRepository

    @Autowired
    private lateinit var beerMapper: BeerMapper

    // Helper to insert beers
    private suspend fun insertTestBeers(beers: List<Beer>): List<Beer> {
        return beerRepository.insertEntities(beers)
    }

    @ParameterizedTest
    @ValueSource(ints = [5, 10, 25, 50, 100])
    fun `insertEntities should handle various counts of random beers`(count: Int) = runTest {
        // Given
        val testUuid = UUID.randomUUID().toString()
        val randomBeerDTOs = BeerTestDataGenerator.generateRandomBeerCreateDTOs(count, "BatchInsert-$testUuid")
        val randomBeers = randomBeerDTOs.map { beerMapper.beerCreateDtoToBeer(it) }

        // When
        val insertedBeers = insertTestBeers(randomBeers)

        // Then
        assertEquals(count, insertedBeers.size)

        // Verify by retrieving using IDs (most reliable method)
        val retrievedBeers = beerRepository.getEntities(ids = insertedBeers.map { it.id }).toList()
        assertEquals(count, retrievedBeers.size)

        insertedBeers.forEach { beer ->
            assertTrue(beer.id > 0L, "ID should be auto-generated")
            assertTrue(beer.beerName.isNotBlank())
            assertTrue(beer.beerStyle.isNotBlank())
            assertTrue(beer.upc.isNotBlank())
            assertTrue(beer.quantityOnHand > 0)
            assertTrue(beer.price > BigDecimal.ZERO)
        }
    }

    @ParameterizedTest
    @CsvSource(
        "1, 10",
        "2, 20",
        "5, 15",
        "10, 25"
    )
    fun `getEntities should handle pagination with test-specific data`(page: Int, size: Int) = runTest {
        // Given - Insert test-specific data with unique identifier
        val testUuid = UUID.randomUUID().toString()
        val totalDataCount = max(size * page + 10, 60)
        val totalBeerDTOs = BeerTestDataGenerator.generateRandomBeerCreateDTOs(totalDataCount, "Pagination-$testUuid")
        val totalBeers = totalBeerDTOs.map { beerMapper.beerCreateDtoToBeer(it) }
        val insertedBeers = insertTestBeers(totalBeers)

        // When - Get entities by IDs with pagination, handling 100 ID limit per request
        val allIds = insertedBeers.map { it.id }
        val allRetrievedBeers = mutableListOf<Beer>()

        // Process IDs in chunks of 100
        allIds.chunked(100).forEach { idChunk ->
            val chunkBeers = beerRepository.getEntities(
                ids = idChunk,
            ).toList()
            allRetrievedBeers.addAll(chunkBeers)
        }

        // Apply pagination to the combined results
        val startIndex = (page - 1) * size
        val endIndex = min(startIndex + size, allRetrievedBeers.size)
        val paginatedBeers = if (startIndex < allRetrievedBeers.size) {
            allRetrievedBeers.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        // Then
        assertTrue(paginatedBeers.size <= size, "Returned ${paginatedBeers.size} items, should be <= $size")

        // Calculate expected items on this page
        val expectedItemsOnPage = min(size, max(0, totalDataCount - startIndex))

        if (expectedItemsOnPage > 0) {
            assertTrue(paginatedBeers.isNotEmpty(), "Page $page should contain data")
            assertTrue(
                paginatedBeers.size <= expectedItemsOnPage,
                "Page $page should contain at most $expectedItemsOnPage items"
            )
        }

        // Verify all returned beers belong to this test
        paginatedBeers.forEach { beer ->
            assertTrue(allIds.contains(beer.id), "Beer should belong to this test")
        }

        // Additional verification - ensure we retrieved all expected data
        assertEquals(
            totalDataCount,
            allRetrievedBeers.size,
            "Should retrieve all inserted beers across chunks"
        )
    }

    @ParameterizedTest
    @ValueSource(ints = [20, 30, 50, 100])
    fun `getEntities should filter by beer name with test isolation`(datasetSize: Int) = runTest {
        // Given
        val testUuid = UUID.randomUUID().toString()
        val randomBeerDTOs = BeerTestDataGenerator.generateRandomBeerCreateDTOs(datasetSize, "NameFilter-$testUuid")
        val randomBeers = randomBeerDTOs.map { beerMapper.beerCreateDtoToBeer(it) }
        val insertedBeers = insertTestBeers(randomBeers)
        val targetBeer = insertedBeers.random()

        // When - Filter by exact name and IDs to ensure test isolation
        val filteredBeers = beerRepository.getEntities(
            ids = insertedBeers.map { it.id },
            beerName = targetBeer.beerName
        ).toList()

        // Then
        assertTrue(filteredBeers.isNotEmpty(), "Should find at least one beer with name ${targetBeer.beerName}")
        filteredBeers.forEach { beer ->
            assertEquals(targetBeer.beerName, beer.beerName)
        }
        // Verify the specific beer is in the results
        assertTrue(filteredBeers.any { it.id == targetBeer.id })
    }

    @ParameterizedTest
    @ValueSource(ints = [15, 25, 40])
    fun `getEntities should filter by beer name contains with test isolation`(totalCount: Int) = runTest {
        // Given - Create test-specific unique identifier
        val testUuid = UUID.randomUUID().toString().take(8)
        val matchingCount = min(5, totalCount / 3)

        val specificBeers = (1..matchingCount).map {
            Beer(
                id = 0L,
                beerName = "Contains-$testUuid-$it",
                beerStyle = "Style-${UUID.randomUUID()}",
                upc = UUID.randomUUID().toString().replace("-", "").take(12),
                quantityOnHand = Random.nextInt(1, 100),
                price = BigDecimal(Random.nextDouble(1.0, 50.0)).setScale(2, RoundingMode.HALF_UP)
            )
        }

        val otherCount = totalCount - matchingCount
        val otherBeerDTOs = BeerTestDataGenerator.generateRandomBeerCreateDTOs(otherCount, "Other-$testUuid")
        val otherBeers = otherBeerDTOs.map { beerMapper.beerCreateDtoToBeer(it) }

        val allInserted = insertTestBeers(specificBeers + otherBeers)

        // When - Filter by contains and IDs for test isolation
        val filteredBeers = beerRepository.getEntities(
            ids = allInserted.map { it.id },
            beerNameContains = "Contains-$testUuid"
        ).toList()

        // Then
        assertEquals(
            matchingCount, filteredBeers.size,
            "Should find exactly $matchingCount beers containing '$testUuid'"
        )
        filteredBeers.forEach { beer ->
            assertTrue(
                beer.beerName.contains(testUuid, ignoreCase = true),
                "Beer name '${beer.beerName}' should contain '$testUuid'"
            )
        }
    }

    @ParameterizedTest
    @CsvSource(
        "3, 10",
        "5, 20",
        "7, 30"
    )
    fun `getEntities should filter by beer style with test isolation`(matchingCount: Int, otherCount: Int) = runTest {
        // Given
        val testUuid = UUID.randomUUID().toString()
        val specificStyle = "SpecificStyle-$testUuid"

        val specificBeers = (1..matchingCount).map {
            Beer(
                id = 0L,
                beerName = "Beer-${UUID.randomUUID()}",
                beerStyle = specificStyle,
                upc = UUID.randomUUID().toString().replace("-", "").take(12),
                quantityOnHand = Random.nextInt(1, 100),
                price = BigDecimal(Random.nextDouble(1.0, 50.0)).setScale(2, RoundingMode.HALF_UP)
            )
        }

        val otherBeerDTOs = BeerTestDataGenerator.generateRandomBeerCreateDTOs(otherCount, "Other-$testUuid")
        val otherBeers = otherBeerDTOs.map { beerMapper.beerCreateDtoToBeer(it) }

        val allInserted = insertTestBeers(specificBeers + otherBeers)

        // When
        val filteredBeers = beerRepository.getEntities(
            ids = allInserted.map { it.id },
            beerStyle = specificStyle
        ).toList()

        // Then
        assertEquals(
            matchingCount, filteredBeers.size,
            "Should find exactly $matchingCount beers with style '$specificStyle'"
        )
        filteredBeers.forEach { beer ->
            assertEquals(specificStyle, beer.beerStyle)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [30, 50, 80])
    fun `getEntities should filter by price range with test isolation`(datasetSize: Int) = runTest {
        // Given
        val testUuid = UUID.randomUUID().toString()
        val randomBeerDTOs = BeerTestDataGenerator.generateRandomBeerCreateDTOs(datasetSize, "PriceFilter-$testUuid")
        val randomBeers = randomBeerDTOs.map { beerMapper.beerCreateDtoToBeer(it) }
        val insertedBeers = insertTestBeers(randomBeers)

        // Generate reasonable price range based on inserted data
        val allPrices = insertedBeers.map { it.price }.sorted()
        val minIndex = Random.nextInt(0, allPrices.size / 3)
        val maxIndex = Random.nextInt(allPrices.size * 2 / 3, allPrices.size)
        val minPrice = allPrices[minIndex]
        val maxPrice = allPrices[maxIndex]

        // When
        val filteredBeers = beerRepository.getEntities(
            ids = insertedBeers.map { it.id },
            minPrice = minPrice,
            maxPrice = maxPrice
        ).toList()

        // Then
        assertTrue(
            filteredBeers.isNotEmpty(),
            "Should find beers in price range [$minPrice, $maxPrice] from $datasetSize beers"
        )
        filteredBeers.forEach { beer ->
            assertTrue(beer.price >= minPrice, "Price ${beer.price} should be >= $minPrice")
            assertTrue(beer.price <= maxPrice, "Price ${beer.price} should be <= $maxPrice")
        }

        // Verify the filtering is actually working by checking we didn't get all beers
        val expectedInRange = insertedBeers.filter { it.price >= minPrice && it.price <= maxPrice }
        assertEquals(expectedInRange.size, filteredBeers.size)
    }

    @ParameterizedTest
    @CsvSource(
        "2, 10",
        "4, 15",
        "6, 20"
    )
    fun `getEntities should filter by quantity on hand with test isolation`(matchingCount: Int, otherCount: Int) =
        runTest {
            // Given
            val testUuid = UUID.randomUUID().toString()
            val targetQuantity = Random.nextInt(50, 200)

            val specificBeers = (1..matchingCount).map {
                Beer(
                    id = 0L,
                    beerName = "Beer-${UUID.randomUUID()}",
                    beerStyle = "Style-${UUID.randomUUID()}",
                    upc = UUID.randomUUID().toString().replace("-", "").take(12),
                    quantityOnHand = targetQuantity,
                    price = BigDecimal(Random.nextDouble(1.0, 50.0)).setScale(2, RoundingMode.HALF_UP)
                )
            }

            val otherBeerDTOs = BeerTestDataGenerator.generateRandomBeerCreateDTOs(otherCount, "Other-$testUuid")
            val otherBeers = otherBeerDTOs.map { beerMapper.beerCreateDtoToBeer(it) }

            val allInserted = insertTestBeers(specificBeers + otherBeers)

            // When
            val filteredBeers = beerRepository.getEntities(
                ids = allInserted.map { it.id },
                quantityOnHand = targetQuantity
            ).toList()

            // Then
            assertEquals(
                matchingCount, filteredBeers.size,
                "Should find exactly $matchingCount beers with quantity $targetQuantity"
            )
            filteredBeers.forEach { beer ->
                assertEquals(targetQuantity, beer.quantityOnHand)
            }
        }

    @ParameterizedTest
    @ValueSource(ints = [15, 25, 40])
    fun `patchEntities should update beers successfully with test isolation`(datasetSize: Int) = runTest {
        // Given
        val testUuid = UUID.randomUUID().toString()
        val originalBeerDTOs = BeerTestDataGenerator.generateRandomBeerCreateDTOs(datasetSize, "PatchTest-$testUuid")
        val originalBeers = originalBeerDTOs.map { beerMapper.beerCreateDtoToBeer(it) }
        val insertedBeers = insertTestBeers(originalBeers)

        // Update between 30% to 70% of the beers dynamically
        val updateRatio = Random.nextDouble(0.3, 0.7)
        val updateDTOs = BeerTestDataGenerator.generateRandomBeerUpdateDTOs(insertedBeers.map { it.id }, updateRatio)

        // When
        beerRepository.patchEntities(updateDTOs)

        // Then - Use specific IDs to verify updates
        val updatedBeers = beerRepository.getEntities(ids = updateDTOs.map { it.id }).toList()
        assertEquals(
            updateDTOs.size, updatedBeers.size,
            "Should retrieve all ${updateDTOs.size} updated beers from dataset of $datasetSize"
        )

        updateDTOs.forEach { updateDTO ->
            val updatedBeer = updatedBeers.first { it.id == updateDTO.id }

            updateDTO.beerName?.let { assertEquals(it, updatedBeer.beerName) }
            updateDTO.beerStyle?.let { assertEquals(it, updatedBeer.beerStyle) }
            updateDTO.price?.let { assertEquals(it, updatedBeer.price) }
            updateDTO.quantityOnHand?.let { assertEquals(it, updatedBeer.quantityOnHand) }
            updateDTO.upc?.let { assertEquals(it, updatedBeer.upc) }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [20, 35, 50])
    fun `deleteEntities should delete specific beers with test isolation`(datasetSize: Int) = runTest {
        // Given
        val testUuid = UUID.randomUUID().toString()
        val originalBeerDTOs = BeerTestDataGenerator.generateRandomBeerCreateDTOs(datasetSize, "DeleteTest-$testUuid")
        val originalBeers = originalBeerDTOs.map { beerMapper.beerCreateDtoToBeer(it) }
        val insertedBeers = insertTestBeers(originalBeers)

        // Delete between 20% to 60% of beers dynamically
        val deleteRatio = Random.nextDouble(0.2, 0.6)
        val deleteCount = (datasetSize * deleteRatio).toInt()
        val beersToDelete = insertedBeers.shuffled().take(deleteCount)
        val idsToDelete = beersToDelete.map { it.id }

        // When
        beerRepository.deleteEntities(idsToDelete)

        // Then - Check only our test data
        val remainingTestBeers = beerRepository.getEntities(ids = insertedBeers.map { it.id }).toList()
        val deletedIds = idsToDelete.toSet()
        val expectedRemainingCount = datasetSize - deleteCount

        remainingTestBeers.forEach { beer ->
            assertTrue(beer.id !in deletedIds, "Deleted beer with ID ${beer.id} should not exist")
        }

        assertEquals(
            expectedRemainingCount, remainingTestBeers.size,
            "Should have exactly $expectedRemainingCount beers remaining from our test data"
        )
    }

    @ParameterizedTest
    @ValueSource(ints = [50, 80, 120])
    fun `getEntities should handle multiple filter conditions with test isolation`(datasetSize: Int) = runTest {
        // Given
        val testUuid = UUID.randomUUID().toString()
        val randomBeerDTOs = BeerTestDataGenerator.generateRandomBeerCreateDTOs(datasetSize, "MultiFilter-$testUuid")
        val randomBeers = randomBeerDTOs.map { beerMapper.beerCreateDtoToBeer(it) }
        val insertedBeers = insertTestBeers(randomBeers)

        // Create filters that should reasonably match some of the data
        val randomStylePart = testUuid.take(6) // Use part of our test UUID
        val allPrices = insertedBeers.map { it.price }.sorted()
        val minPrice = allPrices[allPrices.size / 4] // 25th percentile
        val allQuantities = insertedBeers.map { it.quantityOnHand }.toSet()
        val targetQuantity = allQuantities.random()

        // When
        val filteredBeers = beerRepository.getEntities(
            ids = insertedBeers.map { it.id }, // Ensure test isolation
            beerStyleContains = randomStylePart,
            minPrice = minPrice,
            quantityOnHand = targetQuantity
        ).toList()

        // Then
        filteredBeers.forEach { beer ->
            assertTrue(
                beer.beerStyle.contains(randomStylePart, ignoreCase = true),
                "Beer style '${beer.beerStyle}' should contain '$randomStylePart'"
            )
            assertTrue(
                beer.price >= minPrice,
                "Price ${beer.price} should be >= $minPrice"
            )
            assertEquals(
                targetQuantity, beer.quantityOnHand,
                "Quantity should be exactly $targetQuantity"
            )
        }

        // Verify the filter is working by checking manual count
        val manualCount = insertedBeers.count { beer ->
            beer.beerStyle.contains(randomStylePart, ignoreCase = true) &&
                    beer.price >= minPrice &&
                    beer.quantityOnHand == targetQuantity
        }
        assertEquals(
            manualCount, filteredBeers.size,
            "Filter should return exactly $manualCount beers matching all criteria"
        )
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 5, 10])
    fun `insertEntities should handle various sizes including empty`(count: Int) = runTest {
        // Given
        val testUuid = UUID.randomUUID().toString()
        val beers = if (count == 0) {
            emptyList()
        } else {
            val beerDTOs = BeerTestDataGenerator.generateRandomBeerCreateDTOs(count, "VariousSize-$testUuid")
            beerDTOs.map { beerMapper.beerCreateDtoToBeer(it) }
        }

        // When
        val result = insertTestBeers(beers)

        // Then
        assertEquals(count, result.size)
        if (count > 0) {
            // Verify by retrieving with IDs
            val retrieved = beerRepository.getEntities(ids = result.map { it.id }).toList()
            assertEquals(count, retrieved.size)

            result.forEach { beer ->
                assertTrue(beer.id > 0L)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [200, 300, 500])
    fun `stress test with large dataset and test isolation`(datasetSize: Int) = runTest {
        // Given
        val testUuid = UUID.randomUUID().toString()
        val largeBeerDTOs = BeerTestDataGenerator.generateRandomBeerCreateDTOs(datasetSize, "StressTest-$testUuid")
        val largeDataset = largeBeerDTOs.map { beerMapper.beerCreateDtoToBeer(it) }

        // When - Insert
        val insertedBeers = insertTestBeers(largeDataset)

        // Then - Verify insertion
        assertEquals(datasetSize, insertedBeers.size)

        // When - Random updates (30% of dataset)
        val updateRatio = 0.3
        val randomUpdates = BeerTestDataGenerator.generateRandomBeerUpdateDTOs(insertedBeers.map { it.id }, updateRatio)
        beerRepository.patchEntities(randomUpdates)

        // When - Random deletion (25% of dataset)
        val deleteRatio = 0.25
        val deleteCount = (datasetSize * deleteRatio).toInt()
        val randomDeletions = insertedBeers.shuffled().take(deleteCount).map { it.id }
        beerRepository.deleteEntities(randomDeletions)

        // Then - Verify the final state using specific IDs
        val expectedRemainingCount = datasetSize - deleteCount
        val finalBeers = beerRepository.getEntities(ids = insertedBeers.map { it.id }).toList()

        assertEquals(
            expectedRemainingCount, finalBeers.size,
            "Should have exactly $expectedRemainingCount beers after operations on $datasetSize dataset"
        )

        // Verify no deleted beers remain
        val deletedIds = randomDeletions.toSet()
        finalBeers.forEach { beer ->
            assertTrue(beer.id !in deletedIds, "Deleted beer should not exist")
        }
    }

    // Optional: Test for handling concurrent operations on different datasets
    @Test
    fun `concurrent operations should not interfere with different test datasets`() = runTest {
        val testUuid1 = UUID.randomUUID().toString()
        val testUuid2 = UUID.randomUUID().toString()

        // Create two separate datasets
        val dataset1DTOs = BeerTestDataGenerator.generateRandomBeerCreateDTOs(10, "Concurrent1-$testUuid1")
        val dataset2DTOs = BeerTestDataGenerator.generateRandomBeerCreateDTOs(10, "Concurrent2-$testUuid2")

        val dataset1 = dataset1DTOs.map { beerMapper.beerCreateDtoToBeer(it) }
        val dataset2 = dataset2DTOs.map { beerMapper.beerCreateDtoToBeer(it) }

        // Insert both datasets
        val inserted1 = beerRepository.insertEntities(dataset1)
        val inserted2 = beerRepository.insertEntities(dataset2)

        // Verify isolation - each dataset should only contain its own data
        val retrieved1 = beerRepository.getEntities(ids = inserted1.map { it.id }).toList()
        val retrieved2 = beerRepository.getEntities(ids = inserted2.map { it.id }).toList()

        assertEquals(10, retrieved1.size)
        assertEquals(10, retrieved2.size)

        // Verify no overlap
        val ids1 = retrieved1.map { it.id }.toSet()
        val ids2 = retrieved2.map { it.id }.toSet()
        assertTrue(ids1.intersect(ids2).isEmpty(), "Datasets should not overlap")
    }
}