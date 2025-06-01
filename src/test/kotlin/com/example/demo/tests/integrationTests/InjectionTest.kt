package com.example.demo.tests.integrationTests

import com.example.demo.TestContainersConfiguration
import com.example.demo.controllers.BeerController
import com.example.demo.generators.BeerTestDataGenerator
import com.example.demo.mappers.BeerMapper
import com.example.demo.model.*
import com.example.demo.properties.DatabaseProperties
import com.example.demo.properties.InitialDatabaseProperties
import com.example.demo.repositories.BeerRepository
import com.example.demo.services.BeerService
import com.example.demo.services.BeerServiceImpl
import com.example.demo.services.DatabaseInitService
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

// The following two annotations are enabled if you use Testcontainers

//@Testcontainers
//@Import(TestContainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InjectionTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var database: R2dbcDatabase

    @Autowired
    private lateinit var beerRepository: BeerRepository

    @Autowired
    private lateinit var beerService: BeerService

    //    @Autowired
    private lateinit var beerController: BeerController

    @Autowired
    private lateinit var beerMapper: BeerMapper

    private lateinit var databaseInitService: DatabaseInitService

    @Autowired
    private lateinit var databaseProperties: DatabaseProperties

    @Autowired
    private lateinit var initialDatabaseProperties: InitialDatabaseProperties

    private var initialized = false

    // Track created IDs for each test to avoid interference
    private val testCreatedIds = mutableSetOf<Long>()

//    @BeforeEach
//    fun setUp() = runTest {
//        if (!initialized) {
//            beerService = BeerServiceImpl(BeerRepository(database), beerMapper)
//            databaseInitService = DatabaseInitService(database, databaseProperties, initialDatabaseProperties)
//            databaseInitService.initializeDatabase()
//            databaseInitService.createTables()
//            beerController = BeerController(beerService)
//
//            webTestClient = WebTestClient
//                .bindToController(beerController)
//                .configureClient()
//                .build()
//            initialized = true
//        }
//
//        // Clear test tracking for each test
//        testCreatedIds.clear()
//    }

//    @AfterEach
//    fun tearDown() = runTest {
//        // Clean up only the data created in this specific test
//        if (testCreatedIds.isNotEmpty()) {
//            try {
//                beerService.deleteEntitiesById(testCreatedIds.toList())
//            } catch (e: Exception) {
//                // Ignore cleanup errors - some tests might have already deleted the data
//            }
//        }
//        testCreatedIds.clear()
//    }

    // CREATE Tests - Parameterized with various counts
    @ParameterizedTest
    @ValueSource(ints = [5])
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
            .expectBody<ServiceResult<List<BeerDTO>>>()
            .returnResult()

        val serviceResult = response.responseBody!!
        assertTrue(serviceResult is ServiceResult.Ok, "Expected successful ServiceResult")
        val createdBeers = serviceResult.value
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
}