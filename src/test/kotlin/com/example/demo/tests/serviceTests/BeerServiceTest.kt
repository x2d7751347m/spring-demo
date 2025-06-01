package com.example.demo.tests.serviceTests

import com.example.demo.TestContainersConfiguration
import com.example.demo.mappers.BeerMapper
import com.example.demo.model.BeerCreateDTO
import com.example.demo.model.BeerSearchRequest
import com.example.demo.model.BeerUpdateDTO
import com.example.demo.repositories.BeerRepository
import com.example.demo.services.BeerService
import com.example.demo.services.BeerServiceImpl
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.komapper.r2dbc.R2dbcDatabase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import kotlin.random.Random

@Testcontainers
@SpringBootTest
@Import(TestContainersConfiguration::class)
class BeerServiceTest {

    @Autowired
    private lateinit var database: R2dbcDatabase

    private lateinit var beerRepository: BeerRepository
    private lateinit var beerService: BeerService

    @Autowired
    private lateinit var beerMapper: BeerMapper
    private var initialized = false

    @BeforeEach
    fun setUp() = runTest {
        if (!initialized) {
            beerRepository = BeerRepository(database)
            beerService = BeerServiceImpl(beerRepository, beerMapper)
            initialized = true
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 3, 5, 10, 25])
    fun `saveNewEntities - should create varying number of beers and verify in database`(count: Int) = runTest {
        // Given - Create dynamic test data based on parameter
        val testId = generateTestId()
        val beerCreateDTOs = generateTestBeers(count, testId)

        // When
        val savedBeers = beerService.saveNewEntities(beerCreateDTOs)

        // Then
        assertThat(savedBeers).hasSize(count)

        // Verify all have valid IDs
        savedBeers.forEach { beer ->
            assertThat(beer.id).isGreaterThan(0)
        }

        // Verify by searching with exact IDs (not by name to avoid interference)
        val savedIds = savedBeers.map { it.id }
        val foundBeers = findBeersByIds(savedIds)

        assertThat(foundBeers).hasSize(count)

        // Verify data integrity by checking our test ID in names
        foundBeers.forEach { beer ->
            assertThat(beer.beerName).contains(testId)
        }
    }

    @ParameterizedTest
    @CsvSource(
        "IPA, 3",
        "Stout, 5",
        "Lager, 7",
        "Ale, 4"
    )
    fun `getEntities - should return correct count for beer styles`(style: String, expectedCount: Int) = runTest {
        // Given - Create test data with specific style counts and unique test ID
        val testId = generateTestId()
        val testBeers = createBeersForStyleTest(style, expectedCount, testId)

        val savedBeers = beerService.saveNewEntities(testBeers)
        val savedIds = savedBeers.map { it.id }

        // When - Search by style within our test data only
        val results = findBeersByIdsWithStyle(savedIds, style)

        // Then
        assertThat(results).hasSize(expectedCount)
        results.forEach { beer ->
            assertThat(beer.beerStyle).isEqualTo(style)
            assertThat(beer.beerName).contains(testId)
        }
    }

    @ParameterizedTest
    @CsvSource(
        "10.00, 15.00, 2",
        "15.00, 20.00, 3",
        "20.00, 25.00, 2",
        "25.00, 35.00, 1"
    )
    fun `getEntities - should return correct count for price ranges`(
        minPrice: BigDecimal,
        maxPrice: BigDecimal,
        expectedCount: Int,
    ) = runTest {
        // Given - Create test data with specific price distributions
        val testId = generateTestId()
        val testBeers = createBeersForPriceTest(testId)

        val savedBeers = beerService.saveNewEntities(testBeers)
        val savedIds = savedBeers.map { it.id }

        // When - Find beers within price range from our test data only
        val results = findBeersByIdsWithPriceRange(savedIds, minPrice, maxPrice)

        // Then
        assertThat(results).hasSize(expectedCount)
        results.forEach { beer ->
            assertThat(beer.price)
                .isGreaterThanOrEqualTo(minPrice)
                .isLessThanOrEqualTo(maxPrice)
            assertThat(beer.beerName).contains(testId)
        }
    }

    @ParameterizedTest
    @CsvSource(
        "1, 5, 5",   // page 1, size 5, expect 5
        "2, 5, 5",   // page 2, size 5, expect 5
        "3, 5, 2",   // page 3, size 5, expect 2 (remaining)
        "1, 10, 10", // page 1, size 10, expect 10
        "2, 10, 2"   // page 2, size 10, expect 2 (remaining)
    )
    fun `getEntities - should handle pagination correctly`(page: Int, size: Int, expectedCount: Int) = runTest {
        // Given - Create exactly 12 test beers for predictable pagination
        val totalBeers = 12
        val testId = generateTestId()
        val testBeers = generateTestBeers(totalBeers, testId)

        val savedBeers = beerService.saveNewEntities(testBeers)
        val savedIds = savedBeers.map { it.id }.sorted()

        // When - Apply pagination to our specific test data only
        val paginatedResults = findBeersByIdsWithPagination(savedIds, page, size)

        // Then
        assertThat(paginatedResults).hasSize(expectedCount)
        paginatedResults.forEach { beer ->
            assertThat(beer.beerName).contains(testId)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [2, 5, 8, 15])
    fun `patchEntities - should update varying number of beers`(updateCount: Int) = runTest {
        // Given - Create more beers than we'll update
        val totalBeers = updateCount + 5
        val testId = generateTestId()
        val initialBeers = generateTestBeers(totalBeers, testId)
        val savedBeers = beerService.saveNewEntities(initialBeers)

        // When - Update first 'updateCount' beers
        val beersToUpdate = savedBeers.take(updateCount)
        val updateDTOs = beersToUpdate.mapIndexed { index, beer ->
            BeerUpdateDTO(
                id = beer.id,
                beerName = "Updated-Beer-$index-$testId",
                price = BigDecimal("${20.00 + index}"),
                quantityOnHand = 200 + index * 10
            )
        }

        beerService.patchEntities(updateDTOs)

        // Then - Verify updates by checking specific IDs
        val updatedIds = beersToUpdate.map { it.id }
        val unchangedIds = savedBeers.drop(updateCount).map { it.id }

        val updatedBeers = findBeersByIds(updatedIds)
        val unchangedBeers = findBeersByIds(unchangedIds)

        assertThat(updatedBeers).hasSize(updateCount)
        assertThat(unchangedBeers).hasSize(totalBeers - updateCount)

        // Verify update values
        updatedBeers.sortedBy { it.id }.forEachIndexed { index, beer ->
            assertThat(beer.beerName).isEqualTo("Updated-Beer-$index-$testId")
            assertThat(beer.price).isEqualByComparingTo(BigDecimal("${20.00 + index}"))
            assertThat(beer.quantityOnHand).isEqualTo(200 + index * 10)
        }

        // Verify unchanged beers still contain original test ID pattern
        unchangedBeers.forEach { beer ->
            assertThat(beer.beerName).contains(testId)
            assertThat(beer.beerName).doesNotContain("Updated-Beer")
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 3, 7, 12])
    fun `deleteEntitiesById - should delete exact number of beers`(deleteCount: Int) = runTest {
        // Given - Create more beers than we'll delete
        val totalBeers = deleteCount + 5
        val testId = generateTestId()
        val testBeers = generateTestBeers(totalBeers, testId)
        val savedBeers = beerService.saveNewEntities(testBeers)

        val toDeleteIds = savedBeers.take(deleteCount).map { it.id }
        val shouldRemainIds = savedBeers.drop(deleteCount).map { it.id }

        // When
        beerService.deleteEntitiesById(toDeleteIds)

        // Then - Verify by checking specific IDs
        val remainingBeers = findBeersByIds(shouldRemainIds)
        val deletedBeersCheck = findBeersByIds(toDeleteIds)

        assertThat(remainingBeers).hasSize(totalBeers - deleteCount)
        assertThat(deletedBeersCheck).isEmpty()

        // Verify remaining beers still contain our test ID
        remainingBeers.forEach { beer ->
            assertThat(beer.beerName).contains(testId)
        }
    }

    @ParameterizedTest
    @CsvSource(
        "IPA, 15.00, 20.00, 2",     // IPA beers in price range 15-20, expect 2
        "Stout, 20.00, 30.00, 1",  // Stout beers in price range 20-30, expect 1
        "Lager, 10.00, 15.00, 2",  // Lager beers in price range 10-15, expect 2
        "Ale, 25.00, 35.00, 0"     // Ale beers in price range 25-35, expect 0
    )
    fun `complex search scenarios - should handle multiple criteria dynamically`(
        style: String,
        minPrice: BigDecimal,
        maxPrice: BigDecimal,
        expectedCount: Int,
    ) = runTest {
        // Given - Create diverse test data with predictable distribution
        val testId = generateTestId()
        val testBeers = createBeersForComplexTest(testId)

        val savedBeers = beerService.saveNewEntities(testBeers)
        val savedIds = savedBeers.map { it.id }

        // When - Find beers matching multiple criteria from our test data only
        val results = findBeersByIdsWithStyleAndPrice(savedIds, style, minPrice, maxPrice)

        // Then
        assertThat(results).hasSize(expectedCount)
        results.forEach { beer ->
            assertThat(beer.beerStyle).isEqualTo(style)
            assertThat(beer.price)
                .isGreaterThanOrEqualTo(minPrice)
                .isLessThanOrEqualTo(maxPrice)
            assertThat(beer.beerName).contains(testId)
        }
    }

    @Test
    fun `edge cases - should handle empty and invalid scenarios gracefully`() = runTest {
        // Test empty list operations
        val emptyInsert = beerService.saveNewEntities(emptyList())
        assertThat(emptyInsert).isEmpty()

        // Test empty update - should not throw exceptions
        beerService.patchEntities(emptyList())

        beerService.deleteEntitiesById(emptyList())

        // Test update with non-existent ID - should not throw exceptions
        beerService.patchEntities(
            listOf(BeerUpdateDTO(id = 99999L, beerName = "Non-existent"))
        )

        // Test search with specific non-existent IDs to avoid interference
        val noResults = findBeersByIds(listOf(99998L, 99999L))
        assertThat(noResults).isEmpty()
    }

    @Test
    fun `search with name pattern - should only return our test data`() = runTest {
        // Given - Create test data with specific name pattern
        val testId = generateTestId()
        val testBeers = generateTestBeers(5, testId)
        val savedBeers = beerService.saveNewEntities(testBeers)
        val savedIds = savedBeers.map { it.id }

        // When - Search by name pattern within our IDs only
        val results = beerService.getEntities(
            BeerSearchRequest(
                ids = savedIds,
                beerNameContains = testId,
                page = 1,
                size = 10
            )
        ).toList()

        // Then
        assertThat(results).hasSize(5)
        results.forEach { beer ->
            assertThat(beer.beerName).contains(testId)
        }
    }

    // Helper methods for finding beers by IDs to avoid interference
    private suspend fun findBeersByIds(ids: List<Long>) =
        beerService.getEntities(BeerSearchRequest(ids = ids, page = 1, size = 1000))
            .toList()

    private suspend fun findBeersByIdsWithStyle(ids: List<Long>, style: String) =
        beerService.getEntities(BeerSearchRequest(ids = ids, beerStyle = style, page = 1, size = 1000))
            .toList()

    private suspend fun findBeersByIdsWithPriceRange(ids: List<Long>, minPrice: BigDecimal, maxPrice: BigDecimal) =
        beerService.getEntities(
            BeerSearchRequest(
                ids = ids,
                minPrice = minPrice,
                maxPrice = maxPrice,
                page = 1,
                size = 1000
            )
        )
            .toList()

    private suspend fun findBeersByIdsWithStyleAndPrice(
        ids: List<Long>,
        style: String,
        minPrice: BigDecimal,
        maxPrice: BigDecimal,
    ) = beerService.getEntities(
        BeerSearchRequest(
            ids = ids,
            beerStyle = style,
            minPrice = minPrice,
            maxPrice = maxPrice,
            page = 1,
            size = 1000
        )
    ).toList()

    private suspend fun findBeersByIdsWithPagination(ids: List<Long>, page: Int, size: Int) =
        beerService.getEntities(BeerSearchRequest(ids = ids))
            .toList()

    // Helper methods for dynamic test data generation
    private fun generateTestBeers(count: Int, testId: String): List<BeerCreateDTO> {
        return (0 until count).map { index ->
            BeerCreateDTO(
                beerName = "Beer-$index-$testId",
                beerStyle = listOf("IPA", "Stout", "Lager", "Ale")[index % 4],
                upc = generateUPC(),
                quantityOnHand = Random.nextInt(10, 200),
                price = BigDecimal("${Random.nextDouble(10.0, 30.0)}").setScale(2, java.math.RoundingMode.HALF_UP)
            )
        }
    }

    private fun createBeersForStyleTest(targetStyle: String, expectedCount: Int, testId: String): List<BeerCreateDTO> {
        val testBeers = mutableListOf<BeerCreateDTO>()

        // Add beers of the target style
        repeat(expectedCount) { index ->
            testBeers.add(
                BeerCreateDTO(
                    beerName = "$targetStyle-Beer-$index-$testId",
                    beerStyle = targetStyle,
                    upc = generateUPC(),
                    quantityOnHand = Random.nextInt(10, 200),
                    price = BigDecimal("${Random.nextDouble(10.0, 30.0)}")
                )
            )
        }

        // Add some beers of other styles to test filtering works properly
        repeat(3) { index ->
            testBeers.add(
                BeerCreateDTO(
                    beerName = "Other-Beer-$index-$testId",
                    beerStyle = "Other",
                    upc = generateUPC(),
                    quantityOnHand = Random.nextInt(10, 200),
                    price = BigDecimal("${Random.nextDouble(10.0, 30.0)}")
                )
            )
        }

        return testBeers
    }

    private fun createBeersForPriceTest(testId: String): List<BeerCreateDTO> {
        return listOf(
            // 2 beers in 10-15 range
            createBeerWithPrice("Cheap-Beer-1-$testId", BigDecimal("12.50")),
            createBeerWithPrice("Cheap-Beer-2-$testId", BigDecimal("14.99")),

            // 3 beers in 15-20 range
            createBeerWithPrice("Mid-Beer-1-$testId", BigDecimal("16.00")),
            createBeerWithPrice("Mid-Beer-2-$testId", BigDecimal("18.50")),
            createBeerWithPrice("Mid-Beer-3-$testId", BigDecimal("19.99")),

            // 2 beers in 20-25 range
            createBeerWithPrice("Expensive-Beer-1-$testId", BigDecimal("22.00")),
            createBeerWithPrice("Expensive-Beer-2-$testId", BigDecimal("24.99")),

            // 1 beer in 25-35 range
            createBeerWithPrice("Premium-Beer-$testId", BigDecimal("29.99"))
        )
    }

    private fun createBeersForComplexTest(testId: String): List<BeerCreateDTO> {
        return listOf(
            // IPA beers (2 in 15-20 range)
            createBeerWithStyleAndPrice("American-IPA-$testId", "IPA", BigDecimal("16.99")),
            createBeerWithStyleAndPrice("English-IPA-$testId", "IPA", BigDecimal("18.99")),
            createBeerWithStyleAndPrice("Double-IPA-$testId", "IPA", BigDecimal("24.99")),

            // Stout beers (1 in 20-30 range)
            createBeerWithStyleAndPrice("Imperial-Stout-$testId", "Stout", BigDecimal("22.99")),
            createBeerWithStyleAndPrice("Milk-Stout-$testId", "Stout", BigDecimal("14.99")),

            // Lager beers (2 in 10-15 range)
            createBeerWithStyleAndPrice("Light-Lager-$testId", "Lager", BigDecimal("12.99")),
            createBeerWithStyleAndPrice("Pilsner-$testId", "Lager", BigDecimal("14.99")),
            createBeerWithStyleAndPrice("Märzen-$testId", "Lager", BigDecimal("18.99")),

            // Ale beers (0 in 25-35 range - all below 25)
            createBeerWithStyleAndPrice("Pale-Ale-$testId", "Ale", BigDecimal("16.99")),
            createBeerWithStyleAndPrice("Brown-Ale-$testId", "Ale", BigDecimal("18.99"))
        )
    }

    private fun createBeerWithPrice(name: String, price: BigDecimal): BeerCreateDTO {
        return BeerCreateDTO(
            beerName = name,
            beerStyle = "Test-Style",
            upc = generateUPC(),
            quantityOnHand = Random.nextInt(10, 200),
            price = price
        )
    }

    private fun createBeerWithStyleAndPrice(name: String, style: String, price: BigDecimal): BeerCreateDTO {
        return BeerCreateDTO(
            beerName = name,
            beerStyle = style,
            upc = generateUPC(),
            quantityOnHand = Random.nextInt(10, 200),
            price = price
        )
    }

    private fun generateUPC(): String {
        return Random.nextLong(100000000000L, 999999999999L).toString()
    }

    // Generate unique test ID for each test to prevent interference
    private fun generateTestId(): String {
        return "TEST-${System.currentTimeMillis()}-${Random.nextInt(1000, 9999)}"
    }
}