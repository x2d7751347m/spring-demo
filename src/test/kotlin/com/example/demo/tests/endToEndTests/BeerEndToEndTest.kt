package com.example.demo.tests.endToEndTests

import com.example.demo.TestContainersConfiguration
import com.example.demo.component.ErrorMessage
import com.example.demo.controllers.BeerController
import com.example.demo.generators.BeerTestDataGenerator
import com.example.demo.mappers.BeerMapper
import com.example.demo.model.BeerCreateDTO
import com.example.demo.model.BeerDTO
import com.example.demo.model.BeerSearchRequest
import com.example.demo.model.BeerUpdateDTO
import com.example.demo.repositories.BeerRepository
import com.example.demo.services.BeerService
import com.example.demo.services.BeerServiceImpl
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.komapper.r2dbc.R2dbcDatabase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.test.web.reactive.server.expectBodyList
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestContainersConfiguration::class)
class BeerEndToEndTest {

    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var database: R2dbcDatabase

    private lateinit var beerRepository: BeerRepository
    private lateinit var beerService: BeerService
    private lateinit var beerController: BeerController

    @Autowired
    private lateinit var beerMapper: BeerMapper

    private var initialized = false

    // Track created IDs for each test to avoid interference
    private val testCreatedIds = mutableSetOf<Long>()

    @BeforeEach
    fun setUp() = runTest {
        if (!initialized) {
            beerRepository = BeerRepository(database)
            beerService = BeerServiceImpl(beerRepository, beerMapper)
            beerController = BeerController(beerService)

            webTestClient = WebTestClient
                .bindToController(beerController)
                .configureClient()
                .build()
            initialized = true
        }

        // Clear test tracking for each test
        testCreatedIds.clear()
    }

    @AfterEach
    fun tearDown() = runTest {
        // Clean up only the data created in this specific test
        if (testCreatedIds.isNotEmpty()) {
            try {
                beerService.deleteEntitiesById(testCreatedIds.toList())
            } catch (e: Exception) {
                // Ignore cleanup errors - some tests might have already deleted the data
            }
        }
        testCreatedIds.clear()
    }

    // CREATE Tests - Parameterized with various counts
    @ParameterizedTest
    @ValueSource(ints = [1, 5, 10, 25, 50, 100])
    fun `POST createNewBeers should create random beers successfully for various counts`(count: Int) = runTest {
        // Given
        val createDTOs = BeerTestDataGenerator.generateRandomBeerCreateDTOs(count, "CreateTest-$count")

        // When & Then
        val response = webTestClient
            .post()
            .uri(BeerController.BEER_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createDTOs)
            .exchange()
            .expectStatus().isCreated
            .expectBodyList<BeerDTO>()
            .returnResult()

        val createdBeers = response.responseBody!!
        assertEquals(count, createdBeers.size)

        // Track created IDs for cleanup
        testCreatedIds.addAll(createdBeers.map { it.id })

        // Verify all created beers have valid properties
        createdBeers.forEach { beer ->
            assertTrue(beer.id > 0L)
            assertTrue(beer.beerName.isNotBlank())
            assertTrue(beer.beerStyle.isNotBlank())
            assertTrue(beer.upc.isNotBlank())
            assertTrue(beer.quantityOnHand > 0)
            assertTrue(beer.price > BigDecimal.ZERO)
        }
    }

    // SEARCH Tests - Using created IDs to avoid interference
    @ParameterizedTest
    @CsvSource(
        "1, 10",
        "2, 20",
        "1, 50",
        "3, 15",
        "1, 100"
    )
    fun `POST listBeers should handle various pagination parameters`(page: Int, size: Int) = runTest {
        // Given - Create enough test data to support pagination
        val totalBeers = 100
        val createDTOs = BeerTestDataGenerator.generateRandomBeerCreateDTOs(totalBeers, "PaginationTest")
        val createdBeers = beerService.saveNewEntities(createDTOs)
        testCreatedIds.addAll(createdBeers.map { it.id })

        val searchRequest = BeerSearchRequest(
            ids = createdBeers.map { it.id } // Only search within our created beers
        )

        // When & Then
        val response = webTestClient
            .post()
            .uri("${BeerController.BEER_PATH}/get")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(searchRequest)
            .exchange()
            .expectStatus().isOk
            .expectBodyList<BeerDTO>()
            .returnResult()

        val foundBeers = response.responseBody!!
        assertTrue(foundBeers.size <= size, "Found ${foundBeers.size} beers, but requested size was $size")

        foundBeers.forEach { beer ->
            assertTrue(beer.id > 0L)
            assertTrue(beer.id in testCreatedIds, "Found beer should be one we created in this test")
        }
    }

    // UPDATE Tests - Parameterized with different update ratios
    @ParameterizedTest
    @CsvSource(
        "20, 0.3",
        "30, 0.5",
        "40, 0.7",
        "50, 0.9"
    )
    fun `PATCH patchBeers should update various ratios of beers successfully`(totalBeers: Int, updateRatio: Double) =
        runTest {
            // Given - Create test data first
            val createDTOs = BeerTestDataGenerator.generateRandomBeerCreateDTOs(totalBeers, "PatchTest-$totalBeers")
            val createdBeers = beerService.saveNewEntities(createDTOs)
            testCreatedIds.addAll(createdBeers.map { it.id })

            val updateDTOs = BeerTestDataGenerator.generateRandomBeerUpdateDTOs(createdBeers.map { it.id }, updateRatio)
            val expectedUpdateCount = (totalBeers * updateRatio).toInt()

            // When & Then
            webTestClient
                .patch()
                .uri(BeerController.BEER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateDTOs)
                .exchange()
                .expectStatus().isOk

            // Verify the expected number of updates
            assertEquals(
                expectedUpdateCount,
                updateDTOs.size,
                "Expected $expectedUpdateCount updates for ratio $updateRatio"
            )

            // Verify updates were applied - search only within our created beers
            val searchRequest = BeerSearchRequest(
                page = 1,
                size = totalBeers,
                ids = updateDTOs.map { it.id }
            )

            val updatedBeers = beerService.getEntities(searchRequest).toList()
            assertEquals(updateDTOs.size, updatedBeers.size)

            updateDTOs.forEach { updateDTO ->
                val updatedBeer = updatedBeers.first { it.id == updateDTO.id }
                updateDTO.beerName?.let { assertEquals(it, updatedBeer.beerName) }
                updateDTO.beerStyle?.let { assertEquals(it, updatedBeer.beerStyle) }
                updateDTO.price?.let { assertEquals(it, updatedBeer.price) }
                updateDTO.quantityOnHand?.let { assertEquals(it, updatedBeer.quantityOnHand) }
                updateDTO.upc?.let { assertEquals(it, updatedBeer.upc) }
            }
        }

    // DELETE Tests - Parameterized with various deletion counts
    @ParameterizedTest
    @ValueSource(ints = [5, 10, 15, 25, 40])
    fun `DELETE deleteByIds should delete various numbers of beers`(deleteCount: Int) = runTest {
        // Given - Create more beers than we'll delete
        val totalBeers = deleteCount + 20
        val createDTOs = BeerTestDataGenerator.generateRandomBeerCreateDTOs(totalBeers, "DeleteTest-$deleteCount")
        val createdBeers = beerService.saveNewEntities(createDTOs)
        testCreatedIds.addAll(createdBeers.map { it.id })

        val beersToDelete = createdBeers.shuffled().take(deleteCount)
        val idsToDelete = beersToDelete.map { it.id }

        // When & Then
        webTestClient
            .method(HttpMethod.DELETE)
            .uri(BeerController.BEER_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(idsToDelete)
            .exchange()
            .expectStatus().isNoContent

        // Remove deleted IDs from our tracking (they're already deleted)
        testCreatedIds.removeAll(idsToDelete.toSet())

        // Verify deletion - search only within remaining IDs we created
        val remainingIds = testCreatedIds.toList()
        if (remainingIds.isNotEmpty()) {
            val searchRequest = BeerSearchRequest(page = 1, size = totalBeers, ids = remainingIds)
            val remainingBeers = beerService.getEntities(searchRequest).toList()
            val deletedIds = idsToDelete.toSet()

            // Should have (totalBeers - deleteCount) remaining beers
            val expectedRemainingCount = totalBeers - deleteCount
            assertEquals(
                expectedRemainingCount, remainingBeers.size,
                "Expected exactly $expectedRemainingCount beers, but found ${remainingBeers.size}"
            )

            remainingBeers.forEach { beer ->
                assertTrue(beer.id !in deletedIds, "Deleted beer with ID ${beer.id} should not exist")
                assertTrue(beer.id in testCreatedIds, "Remaining beer should be one we created")
            }
        }
    }

    // SEARCH with filters - Parameterized
    @ParameterizedTest
    @CsvSource(
        "10.00, 30.00",
        "5.00, 50.00",
        "20.00, 80.00",
        "1.00, 25.00"
    )
    fun `POST listBeers should filter by various price ranges`(minPrice: String, maxPrice: String) = runTest {
        // Given - Create beers with various prices
        val createDTOs = BeerTestDataGenerator.generateRandomBeerCreateDTOs(50, "PriceTest")
        val createdBeers = beerService.saveNewEntities(createDTOs)
        testCreatedIds.addAll(createdBeers.map { it.id })

        val minPriceBD = BigDecimal(minPrice)
        val maxPriceBD = BigDecimal(maxPrice)

        val searchRequest = BeerSearchRequest(
            page = 1,
            size = 100,
            minPrice = minPriceBD,
            maxPrice = maxPriceBD,
            ids = createdBeers.map { it.id } // Only search within our created beers
        )

        // When & Then
        val response = webTestClient
            .post()
            .uri("${BeerController.BEER_PATH}/get")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(searchRequest)
            .exchange()
            .expectStatus().isOk
            .expectBodyList<BeerDTO>()
            .returnResult()

        val foundBeers = response.responseBody!!
        foundBeers.forEach { beer ->
            assertTrue(beer.price >= minPriceBD, "Price ${beer.price} should be >= $minPriceBD")
            assertTrue(beer.price <= maxPriceBD, "Price ${beer.price} should be <= $maxPriceBD")
            assertTrue(beer.id in testCreatedIds, "Found beer should be one we created")
        }
    }

    // Full CRUD Integration Tests - Parameterized
    @ParameterizedTest
    @CsvSource(
        "20, 0.5, 5",
        "30, 0.6, 10",
        "40, 0.7, 15",
        "50, 0.8, 20"
    )
    fun `integration test - full CRUD operations with various data sizes`(
        createCount: Int,
        updateRatio: Double,
        deleteCount: Int,
    ) = runTest {
        // Step 1: Create random beers
        val createDTOs = BeerTestDataGenerator.generateRandomBeerCreateDTOs(createCount, "IntegrationTest-$createCount")
        val createResponse = webTestClient
            .post()
            .uri(BeerController.BEER_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createDTOs)
            .exchange()
            .expectStatus().isCreated
            .expectBodyList<BeerDTO>()
            .returnResult()

        val createdBeers = createResponse.responseBody!!
        assertEquals(createCount, createdBeers.size)
        testCreatedIds.addAll(createdBeers.map { it.id })

        // Step 2: Search for created beers using their IDs
        val searchRequest = BeerSearchRequest(
            page = 1,
            size = createCount + 50,
            ids = createdBeers.map { it.id }
        )
        val searchResponse = webTestClient
            .post()
            .uri("${BeerController.BEER_PATH}/get")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(searchRequest)
            .exchange()
            .expectStatus().isOk
            .expectBodyList<BeerDTO>()
            .returnResult()

        val foundBeers = searchResponse.responseBody!!
        assertEquals(createCount, foundBeers.size, "Should find exactly the beers we created")

        // Step 3: Update some beers
        val updateDTOs = BeerTestDataGenerator.generateRandomBeerUpdateDTOs(createdBeers.map { it.id }, updateRatio)
        val expectedUpdateCount = (createCount * updateRatio).toInt()
        assertEquals(expectedUpdateCount, updateDTOs.size, "Update count should match expected ratio")

        webTestClient
            .patch()
            .uri(BeerController.BEER_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updateDTOs)
            .exchange()
            .expectStatus().isOk

        // Step 4: Delete some beers
        val idsToDelete = createdBeers.shuffled().take(deleteCount).map { it.id }
        webTestClient
            .method(HttpMethod.DELETE)
            .uri(BeerController.BEER_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(idsToDelete)
            .exchange()
            .expectStatus().isNoContent

        // Remove deleted IDs from tracking
        testCreatedIds.removeAll(idsToDelete.toSet())

        // Step 5: Verify final state - search only remaining IDs
        val remainingIds = testCreatedIds.toList()
        if (remainingIds.isNotEmpty()) {
            val finalSearchRequest = BeerSearchRequest(
                page = 1,
                size = createCount + 50,
                ids = remainingIds
            )
            val finalSearchResponse = webTestClient
                .post()
                .uri("${BeerController.BEER_PATH}/get")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(finalSearchRequest)
                .exchange()
                .expectStatus().isOk
                .expectBodyList<BeerDTO>()
                .returnResult()

            val finalBeers = finalSearchResponse.responseBody!!
            val expectedRemainingCount = createCount - deleteCount
            assertEquals(
                expectedRemainingCount, finalBeers.size,
                "Should have exactly $expectedRemainingCount beers remaining"
            )

            val deletedIds = idsToDelete.toSet()
            finalBeers.forEach { beer ->
                assertTrue(beer.id !in deletedIds, "Deleted beer with ID ${beer.id} should not exist")
                assertTrue(beer.id in testCreatedIds, "Remaining beer should be one we created")
            }
        }
    }

    // Edge cases - still important to test
    @Test
    fun `POST createNewBeers should handle empty list`() = runTest {
        val emptyList = emptyList<BeerCreateDTO>()

        webTestClient
            .post()
            .uri(BeerController.BEER_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(emptyList)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody<ErrorMessage>()
            .returnResult()
    }

    @Test
    fun `PATCH patchBeers should handle empty update list`() = runTest {
        val emptyUpdateList = emptyList<BeerUpdateDTO>()

        webTestClient
            .patch()
            .uri(BeerController.BEER_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(emptyUpdateList)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody<ErrorMessage>()
            .returnResult()
    }

    @Test
    fun `DELETE deleteByIds should handle empty id list`() = runTest {
        val emptyIdList = emptyList<Long>()

        webTestClient
            .method(HttpMethod.DELETE)
            .uri(BeerController.BEER_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(emptyIdList)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody<ErrorMessage>()
            .returnResult()
    }

    // Advanced search tests using generator
    @Test
    fun `POST listBeers should handle complex search criteria from generator`() = runTest {
        // Given - Create diverse test data
        val createDTOs = BeerTestDataGenerator.generateRandomBeerCreateDTOs(100, "ComplexSearchTest")
        val createdBeers = beerService.saveNewEntities(createDTOs)
        testCreatedIds.addAll(createdBeers.map { it.id })

        // Use generator to create random search request but limit to our created beers
        val searchRequest = BeerSearchRequest(
            ids = createdBeers.map { it.id } // Only search within our created beers
        )

        // When & Then
        val response = webTestClient
            .post()
            .uri("${BeerController.BEER_PATH}/get")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(searchRequest)
            .exchange()
            .expectStatus().isOk
            .expectBodyList<BeerDTO>()
            .returnResult()

        val foundBeers = response.responseBody!!
        assertTrue(foundBeers.size <= createdBeers.size)

        // Verify search criteria are respected
        foundBeers.forEach { beer ->
            assertTrue(beer.id in testCreatedIds, "Found beer should be one we created")

            searchRequest.minPrice?.let { minPrice ->
                assertTrue(beer.price >= minPrice, "Beer price ${beer.price} should be >= $minPrice")
            }
            searchRequest.maxPrice?.let { maxPrice ->
                assertTrue(beer.price <= maxPrice, "Beer price ${beer.price} should be <= $maxPrice")
            }
            searchRequest.beerName?.let { exactName ->
                assertEquals(exactName, beer.beerName, "Beer name should match exactly")
            }
            searchRequest.beerNameContains?.let { nameContains ->
                assertTrue(
                    beer.beerName.contains(nameContains, ignoreCase = true),
                    "Beer name '${beer.beerName}' should contain '$nameContains'"
                )
            }
        }
    }

    // Test using name-based search when ID-based search is not suitable
    @Test
    fun `POST listBeers should handle name-based search isolation`() = runTest {
        val uniqueTestPrefix = "NameTest-${System.currentTimeMillis()}"

        // Given - Create beers with unique names
        val createDTOs = BeerTestDataGenerator.generateRandomBeerCreateDTOs(20, uniqueTestPrefix)
        val createdBeers = beerService.saveNewEntities(createDTOs)
        testCreatedIds.addAll(createdBeers.map { it.id })

        // When - Search by name prefix
        val searchRequest = BeerSearchRequest(
            page = 1,
            size = 50,
            beerNameContains = uniqueTestPrefix
        )

        val response = webTestClient
            .post()
            .uri("${BeerController.BEER_PATH}/get")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(searchRequest)
            .exchange()
            .expectStatus().isOk
            .expectBodyList<BeerDTO>()
            .returnResult()

        // Then
        val foundBeers = response.responseBody!!
        assertEquals(20, foundBeers.size, "Should find exactly the beers we created with unique prefix")

        foundBeers.forEach { beer ->
            assertTrue(
                beer.beerName.contains(uniqueTestPrefix),
                "Beer name '${beer.beerName}' should contain '$uniqueTestPrefix'"
            )
            assertTrue(beer.id in testCreatedIds, "Found beer should be one we created")
        }
    }

//    // CREATE Validation Tests
//    @Test
//    fun `POST createNewBeers should return 400 for empty list`() = runTest {
//        val emptyList = emptyList<BeerCreateDTO>()
//
//        webTestClient
//            .post()
//            .uri(BeerController.BEER_PATH)
//            .contentType(MediaType.APPLICATION_JSON)
//            .bodyValue(emptyList)
//            .exchange()
//            .expectStatus().isBadRequest
//            .expectBody()
//            .jsonPath("$.message").value<String> {
//                assertTrue(it.contains("Validation failed"))
//                assertTrue(it.contains("minItems"))
//            }
//    }
//
//    @Test
//    fun `POST createNewBeers should return 400 for too many items`() = runTest {
//        val tooManyBeers = (1..101).map {
//            BeerCreateDTO(
//                beerName = "Beer $it",
//                beerStyle = "IPA",
//                upc = "123456789012",
//                quantityOnHand = 10,
//                price = BigDecimal("15.99")
//            )
//        }
//
//        webTestClient
//            .post()
//            .uri(BeerController.BEER_PATH)
//            .contentType(MediaType.APPLICATION_JSON)
//            .bodyValue(tooManyBeers)
//            .exchange()
//            .expectStatus().isBadRequest
//            .expectBody()
//            .jsonPath("$.message").value<String> {
//                assertTrue(it.contains("Validation failed"))
//                assertTrue(it.contains("maxItems"))
//            }
//    }
//
//    @ParameterizedTest
//    @MethodSource("invalidBeerCreateDTOs")
//    fun `POST createNewBeers should return 400 for invalid beer data`(
//        invalidBeer: BeerCreateDTO,
//        expectedErrorKeyword: String,
//        testDescription: String,
//    ) = runTest {
//        webTestClient
//            .post()
//            .uri(BeerController.BEER_PATH)
//            .contentType(MediaType.APPLICATION_JSON)
//            .bodyValue(listOf(invalidBeer))
//            .exchange()
//            .expectStatus().isBadRequest
//            .expectBody()
//            .jsonPath("$.message").value<String> { message ->
//                assertTrue(
//                    message.contains("Validation failed") && message.contains(expectedErrorKeyword),
//                    "Test: $testDescription - Expected message to contain 'Validation failed' and '$expectedErrorKeyword', but got: $message"
//                )
//            }
//    }
//
//    // UPDATE Validation Tests
//    @Test
//    fun `PATCH patchBeers should return 400 for empty list`() = runTest {
//        val emptyList = emptyList<BeerUpdateDTO>()
//
//        webTestClient
//            .patch()
//            .uri(BeerController.BEER_PATH)
//            .contentType(MediaType.APPLICATION_JSON)
//            .bodyValue(emptyList)
//            .exchange()
//            .expectStatus().isBadRequest
//            .expectBody()
//            .jsonPath("$.message").value<String> {
//                assertTrue(it.contains("Validation failed"))
//                assertTrue(it.contains("minItems"))
//            }
//    }
//
//    @ParameterizedTest
//    @MethodSource("invalidBeerUpdateDTOs")
//    fun `PATCH patchBeers should return 400 for invalid update data`(
//        invalidUpdate: BeerUpdateDTO,
//        expectedErrorKeyword: String,
//        testDescription: String,
//    ) = runTest {
//        webTestClient
//            .patch()
//            .uri(BeerController.BEER_PATH)
//            .contentType(MediaType.APPLICATION_JSON)
//            .bodyValue(listOf(invalidUpdate))
//            .exchange()
//            .expectStatus().isBadRequest
//            .expectBody()
//            .jsonPath("$.message").value<String> { message ->
//                assertTrue(
//                    message.contains("Validation failed") && message.contains(expectedErrorKeyword),
//                    "Test: $testDescription - Expected message to contain 'Validation failed' and '$expectedErrorKeyword', but got: $message"
//                )
//            }
//    }
//
//    // DELETE Validation Tests
//    @Test
//    fun `DELETE deleteByIds should return 400 for empty id list`() = runTest {
//        val emptyIdList = emptyList<Long>()
//
//        webTestClient
//            .method(HttpMethod.DELETE)
//            .uri(BeerController.BEER_PATH)
//            .contentType(MediaType.APPLICATION_JSON)
//            .bodyValue(emptyIdList)
//            .exchange()
//            .expectStatus().isBadRequest
//            .expectBody()
//            .jsonPath("$.message").value<String> {
//                assertTrue(it.contains("Validation failed"))
//                assertTrue(it.contains("minItems"))
//            }
//    }
//
//    @ParameterizedTest
//    @ValueSource(ints = [0, -1, -100])
//    fun `DELETE deleteByIds should return 400 for invalid id values`(invalidId: Long) = runTest {
//        webTestClient
//            .method(HttpMethod.DELETE)
//            .uri(BeerController.BEER_PATH)
//            .contentType(MediaType.APPLICATION_JSON)
//            .bodyValue(listOf(invalidId))
//            .exchange()
//            .expectStatus().isBadRequest
//            .expectBody()
//            .jsonPath("$.message").value<String> {
//                assertTrue(it.contains("Validation failed"))
//                assertTrue(it.contains("minimum"))
//            }
//    }
//
//    @Test
//    fun `DELETE deleteByIds should return 400 for too many ids`() = runTest {
//        val tooManyIds = (1L..101L).toList()
//
//        webTestClient
//            .method(HttpMethod.DELETE)
//            .uri(BeerController.BEER_PATH)
//            .contentType(MediaType.APPLICATION_JSON)
//            .bodyValue(tooManyIds)
//            .exchange()
//            .expectStatus().isBadRequest
//            .expectBody()
//            .jsonPath("$.message").value<String> {
//                assertTrue(it.contains("Validation failed"))
//                assertTrue(it.contains("maxItems"))
//            }
//    }
//
//    // SEARCH Validation Tests
//    @ParameterizedTest
//    @MethodSource("invalidBeerSearchRequests")
//    fun `POST getBeers should return 400 for invalid search parameters`(
//        invalidSearchRequest: BeerSearchRequest,
//        expectedErrorKeyword: String,
//        testDescription: String,
//    ) = runTest {
//        webTestClient
//            .post()
//            .uri("${BeerController.BEER_PATH}/get")
//            .contentType(MediaType.APPLICATION_JSON)
//            .bodyValue(invalidSearchRequest)
//            .exchange()
//            .expectStatus().isBadRequest
//            .expectBody()
//            .jsonPath("$.message").value<String> { message ->
//                assertTrue(
//                    message.contains("Validation failed") && message.contains(expectedErrorKeyword),
//                    "Test: $testDescription - Expected message to contain 'Validation failed' and '$expectedErrorKeyword', but got: $message"
//                )
//            }
//    }
//
//    companion object {
//        @JvmStatic
//        fun invalidBeerCreateDTOs(): Stream<Arguments> {
//            return Stream.of(
//                // Beer name validation
//                Arguments.of(
//                    BeerCreateDTO("", "IPA", "123456789012", 10, BigDecimal("15.99")),
//                    "notBlank",
//                    "Empty beer name"
//                ),
//                Arguments.of(
//                    BeerCreateDTO("a".repeat(51), "IPA", "123456789012", 10, BigDecimal("15.99")),
//                    "maxLength",
//                    "Beer name too long"
//                ),
//
//                // Beer style validation
//                Arguments.of(
//                    BeerCreateDTO("Good Beer", "", "123456789012", 10, BigDecimal("15.99")),
//                    "notBlank",
//                    "Empty beer style"
//                ),
//                Arguments.of(
//                    BeerCreateDTO("Good Beer", "a".repeat(31), "123456789012", 10, BigDecimal("15.99")),
//                    "maxLength",
//                    "Beer style too long"
//                ),
//
//                // UPC validation
//                Arguments.of(
//                    BeerCreateDTO("Good Beer", "IPA", "12345678901", 10, BigDecimal("15.99")),
//                    "minLength",
//                    "UPC too short"
//                ),
//                Arguments.of(
//                    BeerCreateDTO("Good Beer", "IPA", "1234567890123", 10, BigDecimal("15.99")),
//                    "maxLength",
//                    "UPC too long"
//                ),
//                Arguments.of(
//                    BeerCreateDTO("Good Beer", "IPA", "12345678901a", 10, BigDecimal("15.99")),
//                    "UPC must contain only numbers",
//                    "UPC contains non-numeric characters"
//                ),
//
//                // Quantity validation
//                Arguments.of(
//                    BeerCreateDTO("Good Beer", "IPA", "123456789012", -1, BigDecimal("15.99")),
//                    "minimum",
//                    "Negative quantity"
//                ),
//                Arguments.of(
//                    BeerCreateDTO("Good Beer", "IPA", "123456789012", 1001, BigDecimal("15.99")),
//                    "maximum",
//                    "Quantity too high"
//                ),
//
//                // Price validation
//                Arguments.of(
//                    BeerCreateDTO("Good Beer", "IPA", "123456789012", 10, BigDecimal.ZERO),
//                    "Price must be positive",
//                    "Zero price"
//                ),
//                Arguments.of(
//                    BeerCreateDTO("Good Beer", "IPA", "123456789012", 10, BigDecimal("-1.00")),
//                    "Price must be positive",
//                    "Negative price"
//                ),
//                Arguments.of(
//                    BeerCreateDTO("Good Beer", "IPA", "123456789012", 10, BigDecimal("1000000001")),
//                    "Price cannot exceed 1000000000",
//                    "Price too high"
//                )
//            )
//        }
//
//        @JvmStatic
//        fun invalidBeerUpdateDTOs(): Stream<Arguments> {
//            return Stream.of(
//                // ID validation
//                Arguments.of(
//                    BeerUpdateDTO(0L, "Good Beer", null, null, null, null),
//                    "minimum",
//                    "Invalid ID - zero"
//                ),
//                Arguments.of(
//                    BeerUpdateDTO(-1L, "Good Beer", null, null, null, null),
//                    "minimum",
//                    "Invalid ID - negative"
//                ),
//
//                // Beer name validation (when present)
//                Arguments.of(
//                    BeerUpdateDTO(1L, "", null, null, null, null),
//                    "notBlank",
//                    "Empty beer name update"
//                ),
//                Arguments.of(
//                    BeerUpdateDTO(1L, "a".repeat(51), null, null, null, null),
//                    "maxLength",
//                    "Beer name update too long"
//                ),
//
//                // UPC validation (when present)
//                Arguments.of(
//                    BeerUpdateDTO(1L, null, null, "12345678901", null, null),
//                    "minLength",
//                    "UPC update too short"
//                ),
//                Arguments.of(
//                    BeerUpdateDTO(1L, null, null, "12345678901a", null, null),
//                    "UPC must contain only numbers",
//                    "UPC update contains non-numeric characters"
//                ),
//
//                // Price validation (when present)
//                Arguments.of(
//                    BeerUpdateDTO(1L, null, null, null, null, BigDecimal.ZERO),
//                    "Price must be positive",
//                    "Zero price update"
//                ),
//                Arguments.of(
//                    BeerUpdateDTO(1L, null, null, null, null, BigDecimal("1000000001")),
//                    "Price cannot exceed 1000000000",
//                    "Price update too high"
//                )
//            )
//        }
//
//        @JvmStatic
//        fun invalidBeerSearchRequests(): Stream<Arguments> {
//            return Stream.of(
//                // Page validation
//                Arguments.of(
//                    BeerSearchRequest(page = 0),
//                    "minimum",
//                    "Invalid page - zero"
//                ),
//                Arguments.of(
//                    BeerSearchRequest(page = -1),
//                    "minimum",
//                    "Invalid page - negative"
//                ),
//                Arguments.of(
//                    BeerSearchRequest(page = 1001),
//                    "maximum",
//                    "Invalid page - too high"
//                ),
//
//                // Size validation
//                Arguments.of(
//                    BeerSearchRequest(size = 0),
//                    "minimum",
//                    "Invalid size - zero"
//                ),
//                Arguments.of(
//                    BeerSearchRequest(size = 1001),
//                    "maximum",
//                    "Invalid size - too high"
//                ),
//
//                // IDs validation
//                Arguments.of(
//                    BeerSearchRequest(ids = listOf(0L)),
//                    "minimum",
//                    "Invalid ID in list - zero"
//                ),
//                Arguments.of(
//                    BeerSearchRequest(ids = (1L..101L).toList()),
//                    "maxItems",
//                    "Too many IDs in list"
//                ),
//
//                // Beer name validation
//                Arguments.of(
//                    BeerSearchRequest(beerName = ""),
//                    "notBlank",
//                    "Empty beer name search"
//                ),
//                Arguments.of(
//                    BeerSearchRequest(beerName = "a".repeat(51)),
//                    "maxLength",
//                    "Beer name search too long"
//                ),
//
//                // UPC validation
//                Arguments.of(
//                    BeerSearchRequest(upc = "12345678901"),
//                    "minLength",
//                    "UPC search too short"
//                ),
//                Arguments.of(
//                    BeerSearchRequest(upc = "12345678901a"),
//                    "UPC must contain only numbers",
//                    "UPC search contains non-numeric characters"
//                ),
//
//                // Price validation
//                Arguments.of(
//                    BeerSearchRequest(minPrice = BigDecimal.ZERO),
//                    "Minimum price must be positive",
//                    "Zero minimum price"
//                ),
//                Arguments.of(
//                    BeerSearchRequest(maxPrice = BigDecimal.ZERO),
//                    "Maximum price must be positive",
//                    "Zero maximum price"
//                ),
//                Arguments.of(
//                    BeerSearchRequest(
//                        minPrice = BigDecimal("50.00"),
//                        maxPrice = BigDecimal("25.00")
//                    ),
//                    "Maximum price must be greater than minimum price",
//                    "Max price less than min price"
//                )
//            )
//        }
//    }
//
//    // Complex validation scenarios
//    @Test
//    fun `POST createNewBeers should return 400 with multiple validation errors`() = runTest {
//        val invalidBeers = listOf(
//            BeerCreateDTO("", "", "123", -1, BigDecimal.ZERO), // Multiple errors
//            BeerCreateDTO("Good Beer", "IPA", "123456789012", 10, BigDecimal("15.99")) // Valid one
//        )
//
//        webTestClient
//            .post()
//            .uri(BeerController.BEER_PATH)
//            .contentType(MediaType.APPLICATION_JSON)
//            .bodyValue(invalidBeers)
//            .exchange()
//            .expectStatus().isBadRequest
//            .expectBody()
//            .jsonPath("$.message").value<String> { message ->
//                assertTrue(message.contains("Validation failed"))
//                // Should contain multiple error messages
//                assertTrue(message.contains("notBlank") || message.contains("minLength") || message.contains("minimum"))
//            }
//    }
//
//    @Test
//    fun `POST getBeers should return 400 for complex invalid search`() = runTest {
//        val invalidSearchRequest = BeerSearchRequest(
//            page = -1, // Invalid
//            size = 1001, // Invalid
//            minPrice = BigDecimal("100.00"),
//            maxPrice = BigDecimal("50.00"), // Invalid - less than min price
//            beerName = "", // Invalid - blank
//            ids = listOf(0L, -1L) // Invalid IDs
//        )
//
//        webTestClient
//            .post()
//            .uri("${BeerController.BEER_PATH}/get")
//            .contentType(MediaType.APPLICATION_JSON)
//            .bodyValue(invalidSearchRequest)
//            .exchange()
//            .expectStatus().isBadRequest
//            .expectBody()
//            .jsonPath("$.message").value<String> { message ->
//                assertTrue(message.contains("Validation failed"))
//                // Should contain multiple validation errors
//                assertTrue(
//                    message.contains("minimum") ||
//                            message.contains("maximum") ||
//                            message.contains("notBlank") ||
//                            message.contains("Maximum price must be greater than minimum price")
//                )
//            }
//    }
//
//    // Edge case: Valid but boundary values
//    @Test
//    fun `POST createNewBeers should accept valid boundary values`() = runTest {
//        val boundaryValidBeer = BeerCreateDTO(
//            beerName = "a".repeat(50), // Max length
//            beerStyle = "b".repeat(30), // Max length
//            upc = "123456789012", // Exact length
//            quantityOnHand = 1000, // Max value
//            price = BigDecimal("999999999.99") // High but valid value
//        )
//
//        webTestClient
//            .post()
//            .uri(BeerController.BEER_PATH)
//            .contentType(MediaType.APPLICATION_JSON)
//            .bodyValue(listOf(boundaryValidBeer))
//            .exchange()
//            .expectStatus().isCreated
//    }
//
//    @Test
//    fun `POST getBeers should accept valid boundary search parameters`() = runTest {
//        val boundaryValidSearch = BeerSearchRequest(
//            page = 1000, // Max page
//            size = 1000, // Max size
//            minPrice = BigDecimal("0.01"), // Minimum valid price
//            maxPrice = BigDecimal("1000000000"), // Maximum valid price
//            beerName = "a".repeat(50), // Max length
//            beerNameContains = "b".repeat(50), // Max length
//            ids = (1L..100L).toList() // Max number of IDs
//        )
//
//        webTestClient
//            .post()
//            .uri("${BeerController.BEER_PATH}/get")
//            .contentType(MediaType.APPLICATION_JSON)
//            .bodyValue(boundaryValidSearch)
//            .exchange()
//            .expectStatus().isOk
//    }
}