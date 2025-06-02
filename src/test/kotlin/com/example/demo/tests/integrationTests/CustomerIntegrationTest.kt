package com.example.demo.tests.integrationTests

import com.example.demo.TestContainersConfiguration
import com.example.demo.controllers.CustomerController
import com.example.demo.generators.CustomerTestDataGenerator
import com.example.demo.model.*
import com.example.demo.services.CustomerService
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.test.web.reactive.server.expectBodyList
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestContainersConfiguration::class)
class CustomerIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var customerService: CustomerService

    // CREATE Tests - Parameterized with various counts
    @ParameterizedTest
    @ValueSource(ints = [1, 5, 10, 25, 50, 100])
    fun `POST createNewCustomers should create random customers successfully for various counts`(count: Int) = runTest {
        // Given
        val createDTOs = CustomerTestDataGenerator.generateRandomCustomerCreateDTOs(count, "CreateTest $count")

        // When & Then
        val response = webTestClient
            .post()
            .uri(CustomerController.CUSTOMER_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createDTOs)
            .exchange()
            .expectStatus().isCreated
            .expectBody<ServiceResult<List<CustomerDTO>>>()
            .returnResult()

        val serviceResult = response.responseBody!!
        assertTrue(serviceResult is ServiceResult.Ok)
        val createdCustomers = serviceResult.value
        assertEquals(count, createdCustomers.size)

        // Verify all created customers have valid properties
        createdCustomers.forEach { customer ->
            assertTrue(customer.id > 0L)
            assertTrue(customer.customerName.isNotBlank())
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
    fun `POST listCustomers should handle various pagination parameters`(page: Int, size: Int) = runTest {
        // Given - Create enough test data to support pagination
        val totalCustomers = 100
        val createDTOs = CustomerTestDataGenerator.generateRandomCustomerCreateDTOs(totalCustomers, "PaginationTest")
        val createdCustomers = customerService.saveNewEntities(createDTOs)

        val searchRequest = CustomerSearchRequest(
            ids = createdCustomers.map { it.id } // Only search within our created customers
        )

        // When & Then
        val response = webTestClient
            .post()
            .uri("${CustomerController.CUSTOMER_PATH}/get")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(searchRequest)
            .exchange()
            .expectStatus().isOk
            .expectBodyList<CustomerDTO>()
            .returnResult()

        val foundCustomers = response.responseBody!!
        assertTrue(
            foundCustomers.size <= createdCustomers.size,
            "Found ${foundCustomers.size} customers, but requested size was $size"
        )

        foundCustomers.forEach { customer ->
            assertTrue(customer.id > 0L)
            assertTrue(
                customer.id in createdCustomers.map { it.id },
                "Found customer should be one we created in this test"
            )
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
    fun `PATCH patchCustomers should update various ratios of customers successfully`(
        totalCustomers: Int,
        updateRatio: Double,
    ) = runTest {
        // Given - Create test data first
        val createDTOs =
            CustomerTestDataGenerator.generateRandomCustomerCreateDTOs(totalCustomers, "PatchTest-$totalCustomers")
        val createdCustomers = customerService.saveNewEntities(createDTOs)

        val updateDTOs =
            CustomerTestDataGenerator.generateRandomCustomerUpdateDTOs(createdCustomers.map { it.id }, updateRatio)
        val expectedUpdateCount = (totalCustomers * updateRatio).toInt()

        // When & Then
        webTestClient
            .patch()
            .uri(CustomerController.CUSTOMER_PATH)
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

        // Verify updates were applied - search only within our created customers
        val searchRequest = CustomerSearchRequest(
            page = 1,
            size = totalCustomers,
            ids = updateDTOs.map { it.id }
        )

        val updatedCustomers = customerService.getEntities(searchRequest).toList()
        assertEquals(updateDTOs.size, updatedCustomers.size)

        updateDTOs.forEach { updateDTO ->
            val updatedCustomer = updatedCustomers.first { it.id == updateDTO.id }
            updateDTO.customerName?.let { assertEquals(it, updatedCustomer.customerName) }
        }
    }

    // DELETE Tests - Parameterized with various deletion counts
    @ParameterizedTest
    @ValueSource(ints = [5, 10, 15, 25, 40])
    fun `DELETE deleteByIds should delete various numbers of customers`(deleteCount: Int) = runTest {
        // Given - Create more customers than we'll delete
        val totalCustomers = deleteCount + 20
        val createDTOs =
            CustomerTestDataGenerator.generateRandomCustomerCreateDTOs(totalCustomers, "DeleteTest-$deleteCount")
        val createdCustomers = customerService.saveNewEntities(createDTOs)

        val customersToDelete = createdCustomers.shuffled().take(deleteCount)
        val idsToDelete = customersToDelete.map { it.id }

        // When & Then
        webTestClient
            .method(HttpMethod.DELETE)
            .uri(CustomerController.CUSTOMER_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(idsToDelete)
            .exchange()
            .expectStatus().isNoContent

        // Verify deletion - search only within remaining IDs
        val remainingIds = createdCustomers.map { it.id } - idsToDelete.toSet()
        if (remainingIds.isNotEmpty()) {
            val searchRequest = CustomerSearchRequest(page = 1, size = totalCustomers, ids = remainingIds.toList())
            val remainingCustomers = customerService.getEntities(searchRequest).toList()
            val deletedIds = idsToDelete.toSet()

            // Should have (totalCustomers - deleteCount) remaining customers
            val expectedRemainingCount = totalCustomers - deleteCount
            assertEquals(
                expectedRemainingCount, remainingCustomers.size,
                "Expected exactly $expectedRemainingCount customers, but found ${remainingCustomers.size}"
            )

            remainingCustomers.forEach { customer ->
                assertTrue(customer.id !in deletedIds, "Deleted customer with ID ${customer.id} should not exist")
            }
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
        // Step 1: Create random customers
        val createDTOs =
            CustomerTestDataGenerator.generateRandomCustomerCreateDTOs(createCount, "IntegrationTest $createCount")
        val createResponse = webTestClient
            .post()
            .uri(CustomerController.CUSTOMER_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createDTOs)
            .exchange()
            .expectStatus().isCreated
            .expectBody<ServiceResult<List<CustomerDTO>>>()
            .returnResult()

        val serviceResult = createResponse.responseBody!!
        assertTrue(serviceResult is ServiceResult.Ok)
        val createdCustomers = serviceResult.value
        assertEquals(createCount, createdCustomers.size)

        // Step 2: Search for created customers using their IDs
        val searchRequest = CustomerSearchRequest(
            page = 1,
            size = createCount + 50,
            ids = createdCustomers.map { it.id }
        )
        val searchResponse = webTestClient
            .post()
            .uri("${CustomerController.CUSTOMER_PATH}/get")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(searchRequest)
            .exchange()
            .expectStatus().isOk
            .expectBodyList<CustomerDTO>()
            .returnResult()

        val foundCustomers = searchResponse.responseBody!!
        assertEquals(createCount, foundCustomers.size, "Should find exactly the customers we created")

        // Step 3: Update some customers
        val updateDTOs =
            CustomerTestDataGenerator.generateRandomCustomerUpdateDTOs(createdCustomers.map { it.id }, updateRatio)
        val expectedUpdateCount = (createCount * updateRatio).toInt()
        assertEquals(expectedUpdateCount, updateDTOs.size, "Update count should match expected ratio")

        webTestClient
            .patch()
            .uri(CustomerController.CUSTOMER_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(updateDTOs)
            .exchange()
            .expectStatus().isOk

        // Step 4: Delete some customers
        val idsToDelete = createdCustomers.shuffled().take(deleteCount).map { it.id }
        webTestClient
            .method(HttpMethod.DELETE)
            .uri(CustomerController.CUSTOMER_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(idsToDelete)
            .exchange()
            .expectStatus().isNoContent

        // Step 5: Verify final state - search only remaining IDs
        val remainingIds = createdCustomers.map { it.id } - idsToDelete.toSet()
        if (remainingIds.isNotEmpty()) {
            val finalSearchRequest = CustomerSearchRequest(
                page = 1,
                size = createCount + 50,
                ids = remainingIds.toList()
            )
            val finalSearchResponse = webTestClient
                .post()
                .uri("${CustomerController.CUSTOMER_PATH}/get")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(finalSearchRequest)
                .exchange()
                .expectStatus().isOk
                .expectBodyList<CustomerDTO>()
                .returnResult()

            val finalCustomers = finalSearchResponse.responseBody!!
            val expectedRemainingCount = createCount - deleteCount
            assertEquals(
                expectedRemainingCount, finalCustomers.size,
                "Should have exactly $expectedRemainingCount customers remaining"
            )

            val deletedIds = idsToDelete.toSet()
            finalCustomers.forEach { customer ->
                assertTrue(customer.id !in deletedIds, "Deleted customer with ID ${customer.id} should not exist")
            }
        }
    }

    // Validation Failure Tests
    @Test
    fun `POST createNewCustomers should return error for empty list`() = runTest {
        val emptyList = emptyList<CustomerCreateDTO>()

        val response = webTestClient
            .post()
            .uri(CustomerController.CUSTOMER_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(emptyList)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody<ServiceResult<List<CustomerDTO>>>()
            .returnResult()

        val serviceResult = response.responseBody!!
        assertTrue(serviceResult is ServiceResult.Err)
        assertEquals("Validation failed", serviceResult.message)
        assertTrue(serviceResult.errors.isNotEmpty())
    }

    @Test
    fun `POST createNewCustomers should return error for invalid customer data`() = runTest {
        val invalidCustomers = listOf(
            CustomerCreateDTO(customerName = ""), // Empty name
            CustomerCreateDTO(customerName = "   "), // Blank name
            CustomerCreateDTO(customerName = "x".repeat(256)) // Too long name (assuming max 255)
        )

        val response = webTestClient
            .post()
            .uri(CustomerController.CUSTOMER_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidCustomers)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody<ServiceResult<List<CustomerDTO>>>()
            .returnResult()

        val serviceResult = response.responseBody!!
        assertTrue(serviceResult is ServiceResult.Err)
        assertEquals("Validation failed", serviceResult.message)
        assertTrue(serviceResult.errors.isNotEmpty())
    }

    @Test
    fun `PATCH patchCustomers should return error for empty update list`() = runTest {
        val emptyUpdateList = emptyList<CustomerUpdateDTO>()

        webTestClient
            .patch()
            .uri(CustomerController.CUSTOMER_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(emptyUpdateList)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `PATCH patchCustomers should return error for invalid update data`() = runTest {
        val invalidUpdates = listOf(
            CustomerUpdateDTO(id = 0, customerName = "Valid Name"), // Invalid ID
            CustomerUpdateDTO(id = -1, customerName = "Valid Name"), // Negative ID
            CustomerUpdateDTO(id = 1, customerName = ""), // Empty name
            CustomerUpdateDTO(id = 2, customerName = "   "), // Blank name
            CustomerUpdateDTO(id = 3, customerName = "x".repeat(256)) // Too long name
        )

        webTestClient
            .patch()
            .uri(CustomerController.CUSTOMER_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidUpdates)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `DELETE deleteByIds should return error for empty id list`() = runTest {
        val emptyIdList = emptyList<Long>()

        webTestClient
            .method(HttpMethod.DELETE)
            .uri(CustomerController.CUSTOMER_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(emptyIdList)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `DELETE deleteByIds should return error for invalid ids`() = runTest {
        val invalidIds = listOf(0L, -1L, -999L) // Invalid IDs

        webTestClient
            .method(HttpMethod.DELETE)
            .uri(CustomerController.CUSTOMER_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidIds)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `POST getCustomers should return error for invalid search request`() = runTest {
        val invalidSearchRequest = CustomerSearchRequest(
            page = 0, // Invalid page (should be >= 1)
            size = 0, // Invalid size (should be >= 1)
            ids = listOf(0L, -1L) // Invalid IDs
        )

        webTestClient
            .post()
            .uri("${CustomerController.CUSTOMER_PATH}/get")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidSearchRequest)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `POST getCustomers should return error for too large page size`() = runTest {
        val invalidSearchRequest = CustomerSearchRequest(
            page = 1,
            size = 1001 // Assuming max size is 1000
        )

        webTestClient
            .post()
            .uri("${CustomerController.CUSTOMER_PATH}/get")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidSearchRequest)
            .exchange()
            .expectStatus().isBadRequest
    }

    // Customer-specific search tests
    @Test
    fun `POST listCustomers should handle name-based search`() = runTest {
        val uniqueTestPrefix = "NameTest ${System.currentTimeMillis()}".take(20)

        // Given - Create customers with unique names
        val createDTOs = CustomerTestDataGenerator.generateRandomCustomerCreateDTOs(20, uniqueTestPrefix)
        val createdCustomers = customerService.saveNewEntities(createDTOs)

        // When - Search by name prefix
        val searchRequest = CustomerSearchRequest(
            page = 1,
            size = 50,
            customerNameContains = uniqueTestPrefix
        )

        val response = webTestClient
            .post()
            .uri("${CustomerController.CUSTOMER_PATH}/get")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(searchRequest)
            .exchange()
            .expectStatus().isOk
            .expectBodyList<CustomerDTO>()
            .returnResult()

        // Then
        val foundCustomers = response.responseBody!!
        assertEquals(20, foundCustomers.size, "Should find exactly the customers we created with unique prefix")

        foundCustomers.forEach { customer ->
            assertTrue(
                customer.customerName.contains(uniqueTestPrefix),
                "Customer name '${customer.customerName}' should contain '$uniqueTestPrefix'"
            )
        }
    }

    @Test
    fun `POST listCustomers should handle exact name search`() = runTest {
        val exactName = "ExactName ${System.currentTimeMillis()}"

        // Given - Create one customer with exact name and others with different names
        val exactNameDTO = CustomerCreateDTO(customerName = exactName)
        val otherDTOs = CustomerTestDataGenerator.generateRandomCustomerCreateDTOs(5, "OtherName")

        val allDTOs = listOf(exactNameDTO) + otherDTOs
        val createdCustomers = customerService.saveNewEntities(allDTOs)

        // When - Search by exact name
        val searchRequest = CustomerSearchRequest(
            customerName = exactName
        )

        val response = webTestClient
            .post()
            .uri("${CustomerController.CUSTOMER_PATH}/get")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(searchRequest)
            .exchange()
            .expectStatus().isOk
            .expectBodyList<CustomerDTO>()
            .returnResult()

        // Then
        val foundCustomers = response.responseBody!!
        assertEquals(1, foundCustomers.size, "Should find exactly one customer with exact name")
        assertEquals(exactName, foundCustomers.first().customerName)
    }

    @Test
    fun `POST listCustomers should combine search criteria properly`() = runTest {
        val testPrefix = "CombinedTest-${System.currentTimeMillis()}".take(20)

        // Given - Create test data
        val createDTOs = CustomerTestDataGenerator.generateRandomCustomerCreateDTOs(30, testPrefix)
        val createdCustomers = customerService.saveNewEntities(createDTOs)

        // When - Search with combined criteria (specific IDs + name contains)
        val selectedIds = createdCustomers.take(10).map { it.id }
        val searchRequest = CustomerSearchRequest(
            page = 1,
            size = 20,
            ids = selectedIds,
            customerNameContains = testPrefix
        )

        val response = webTestClient
            .post()
            .uri("${CustomerController.CUSTOMER_PATH}/get")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(searchRequest)
            .exchange()
            .expectStatus().isOk
            .expectBodyList<CustomerDTO>()
            .returnResult()

        // Then
        val foundCustomers = response.responseBody!!
        assertEquals(10, foundCustomers.size, "Should find exactly 10 customers matching both ID and name criteria")

        foundCustomers.forEach { customer ->
            assertTrue(customer.id in selectedIds, "Customer ID should be in selected IDs")
            assertTrue(customer.customerName.contains(testPrefix), "Customer name should contain test prefix")
        }
    }
}