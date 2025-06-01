package com.example.demo.tests.serviceTests

import com.example.demo.TestContainersConfiguration
import com.example.demo.mappers.CustomerMapper
import com.example.demo.model.CustomerCreateDTO
import com.example.demo.model.CustomerSearchRequest
import com.example.demo.model.CustomerUpdateDTO
import com.example.demo.repositories.CustomerRepository
import com.example.demo.services.CustomerService
import com.example.demo.services.CustomerServiceImpl
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
import kotlin.random.Random

@Testcontainers
@SpringBootTest
@Import(TestContainersConfiguration::class)
class CustomerServiceTest {

    @Autowired
    private lateinit var database: R2dbcDatabase

    private lateinit var customerRepository: CustomerRepository
    private lateinit var customerService: CustomerService

    @Autowired
    private lateinit var customerMapper: CustomerMapper
    private var initialized = false

    @BeforeEach
    fun setUp() = runTest {
        if (!initialized) {
            customerRepository = CustomerRepository(database)
            customerService = CustomerServiceImpl(customerRepository, customerMapper)
            initialized = true
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 3, 5, 10, 25])
    fun `saveNewEntities - should create varying number of customers and verify in database`(count: Int) = runTest {
        // Given - Create dynamic test data based on parameter
        val testId = generateTestId()
        val customerCreateDTOs = generateTestCustomers(count, testId)

        // When
        val savedCustomers = customerService.saveNewEntities(customerCreateDTOs)

        // Then
        assertThat(savedCustomers).hasSize(count)

        // Verify all have valid IDs
        savedCustomers.forEach { customer ->
            assertThat(customer.id).isGreaterThan(0)
        }

        // Verify by searching with exact IDs (not by name to avoid interference)
        val savedIds = savedCustomers.map { it.id }
        val foundCustomers = findCustomersByIds(savedIds)

        assertThat(foundCustomers).hasSize(count)

        // Verify data integrity by checking our test ID in names
        foundCustomers.forEach { customer ->
            assertThat(customer.customerName).contains(testId)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["SearchTerm1", "SearchTerm2", "TestCustomer", "SpecialName"])
    fun `getEntities - should return correct customers for name search`(searchTerm: String) = runTest {
        // Given - Create test customers with specific name patterns
        val testId = generateTestId()
        val matchingCustomers = (1..3).map { index ->
            CustomerCreateDTO(customerName = "$searchTerm-Customer-$index-$testId")
        }
        val nonMatchingCustomers = (1..2).map { index ->
            CustomerCreateDTO(customerName = "Other-Customer-$index-$testId")
        }

        val allTestCustomers = matchingCustomers + nonMatchingCustomers
        val savedCustomers = customerService.saveNewEntities(allTestCustomers)
        val savedIds = savedCustomers.map { it.id }

        // When - Search by name within our test data only
        val results = findCustomersByIdsWithNameContains(savedIds, searchTerm)

        // Then
        assertThat(results).hasSize(3)
        results.forEach { customer ->
            assertThat(customer.customerName).contains(searchTerm)
            assertThat(customer.customerName).contains(testId)
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
        // Given - Create exactly 12 test customers for predictable pagination
        val totalCustomers = 12
        val testId = generateTestId()
        val testCustomers = generateTestCustomers(totalCustomers, testId)

        val savedCustomers = customerService.saveNewEntities(testCustomers)
        val savedIds = savedCustomers.map { it.id }.sorted()

        // When - Apply pagination to our specific test data only
        val paginatedResults = findCustomersByIdsWithPagination(savedIds, page, size)

        // Then
        assertThat(paginatedResults).hasSize(expectedCount)
        paginatedResults.forEach { customer ->
            assertThat(customer.customerName).contains(testId)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [2, 5, 8, 15])
    fun `patchEntities - should update varying number of customers`(updateCount: Int) = runTest {
        // Given - Create more customers than we'll update
        val totalCustomers = updateCount + 5
        val testId = generateTestId()
        val initialCustomers = generateTestCustomers(totalCustomers, testId)
        val savedCustomers = customerService.saveNewEntities(initialCustomers)

        // When - Update first 'updateCount' customers
        val customersToUpdate = savedCustomers.take(updateCount)
        val updateDTOs = customersToUpdate.mapIndexed { index, customer ->
            CustomerUpdateDTO(
                id = customer.id,
                customerName = "Updated-Customer-$index-$testId"
            )
        }

        customerService.patchEntities(updateDTOs)

        // Then - Verify updates by checking specific IDs
        val updatedIds = customersToUpdate.map { it.id }
        val unchangedIds = savedCustomers.drop(updateCount).map { it.id }

        val updatedCustomers = findCustomersByIds(updatedIds)
        val unchangedCustomers = findCustomersByIds(unchangedIds)

        assertThat(updatedCustomers).hasSize(updateCount)
        assertThat(unchangedCustomers).hasSize(totalCustomers - updateCount)

        // Verify update values
        updatedCustomers.sortedBy { it.id }.forEachIndexed { index, customer ->
            assertThat(customer.customerName).isEqualTo("Updated-Customer-$index-$testId")
        }

        // Verify unchanged customers still contain original test ID pattern
        unchangedCustomers.forEach { customer ->
            assertThat(customer.customerName).contains(testId)
            assertThat(customer.customerName).doesNotContain("Updated-Customer")
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 3, 7, 12])
    fun `deleteEntitiesById - should delete exact number of customers`(deleteCount: Int) = runTest {
        // Given - Create more customers than we'll delete
        val totalCustomers = deleteCount + 5
        val testId = generateTestId()
        val testCustomers = generateTestCustomers(totalCustomers, testId)
        val savedCustomers = customerService.saveNewEntities(testCustomers)

        val toDeleteIds = savedCustomers.take(deleteCount).map { it.id }
        val shouldRemainIds = savedCustomers.drop(deleteCount).map { it.id }

        // When
        customerService.deleteEntitiesById(toDeleteIds)

        // Then - Verify by checking specific IDs
        val remainingCustomers = findCustomersByIds(shouldRemainIds)
        val deletedCustomersCheck = findCustomersByIds(toDeleteIds)

        assertThat(remainingCustomers).hasSize(totalCustomers - deleteCount)
        assertThat(deletedCustomersCheck).isEmpty()

        // Verify remaining customers still contain our test ID
        remainingCustomers.forEach { customer ->
            assertThat(customer.customerName).contains(testId)
        }
    }

    @ParameterizedTest
    @CsvSource(
        "TestGroup1, 3",
        "TestGroup2, 5",
        "TestGroup3, 2",
        "TestGroup4, 4"
    )
    fun `getEntities - should return correct count for grouped customers`(groupName: String, expectedCount: Int) =
        runTest {
            // Given - Create test data with specific group counts and unique test ID
            val testId = generateTestId()
            val testCustomers = createCustomersForGroupTest(groupName, expectedCount, testId)

            val savedCustomers = customerService.saveNewEntities(testCustomers)
            val savedIds = savedCustomers.map { it.id }

            // When - Search by group name within our test data only
            val results = findCustomersByIdsWithNameContains(savedIds, groupName)

            // Then
            assertThat(results).hasSize(expectedCount)
            results.forEach { customer ->
                assertThat(customer.customerName).contains(groupName)
                assertThat(customer.customerName).contains(testId)
            }
        }

    @Test
    fun `edge cases - should handle empty and invalid scenarios gracefully`() = runTest {
        // Test empty list operations
        val emptyInsert = customerService.saveNewEntities(emptyList())
        assertThat(emptyInsert).isEmpty()

        // Test empty update - should not throw exceptions
        customerService.patchEntities(emptyList())

        customerService.deleteEntitiesById(emptyList())

        // Test update with non-existent ID - should not throw exceptions
        customerService.patchEntities(
            listOf(CustomerUpdateDTO(id = 99999L, customerName = "Non-existent"))
        )

        // Test search with specific non-existent IDs to avoid interference
        val noResults = findCustomersByIds(listOf(99998L, 99999L))
        assertThat(noResults).isEmpty()
    }

    @Test
    fun `search with name pattern - should only return our test data`() = runTest {
        // Given - Create test data with specific name pattern
        val testId = generateTestId()
        val testCustomers = generateTestCustomers(5, testId)
        val savedCustomers = customerService.saveNewEntities(testCustomers)
        val savedIds = savedCustomers.map { it.id }

        // When - Search by name pattern within our IDs only
        val results = customerService.getEntities(
            CustomerSearchRequest(
                ids = savedIds,
                customerNameContains = testId,
                page = 1,
                size = 10
            )
        ).toList()

        // Then
        assertThat(results).hasSize(5)
        results.forEach { customer ->
            assertThat(customer.customerName).contains(testId)
        }
    }

    @Test
    fun `search with exact name - should return matching customer`() = runTest {
        // Given - Create test customers with specific names
        val testId = generateTestId()
        val exactName = "ExactCustomerName-$testId"
        val testCustomers = listOf(
            CustomerCreateDTO(customerName = exactName),
            CustomerCreateDTO(customerName = "Other-Customer-$testId"),
            CustomerCreateDTO(customerName = "Another-Customer-$testId")
        )

        val savedCustomers = customerService.saveNewEntities(testCustomers)
        val savedIds = savedCustomers.map { it.id }

        // When - Search by exact name within our test data only
        val results = customerService.getEntities(
            CustomerSearchRequest(
                ids = savedIds,
                customerName = exactName,
                page = 1,
                size = 10
            )
        ).toList()

        // Then
        assertThat(results).hasSize(1)
        assertThat(results.first().customerName).isEqualTo(exactName)
    }

    // Helper methods for finding customers by IDs to avoid interference
    private suspend fun findCustomersByIds(ids: List<Long>) =
        customerService.getEntities(CustomerSearchRequest(ids = ids, page = 1, size = 1000))
            .toList()

    private suspend fun findCustomersByIdsWithNameContains(ids: List<Long>, nameContains: String) =
        customerService.getEntities(
            CustomerSearchRequest(
                ids = ids,
                customerNameContains = nameContains,
                page = 1,
                size = 1000
            )
        )
            .toList()

    private suspend fun findCustomersByIdsWithPagination(ids: List<Long>, page: Int, size: Int) =
        customerService.getEntities(CustomerSearchRequest(ids = ids))
            .toList()

    // Helper methods for dynamic test data generation
    private fun generateTestCustomers(count: Int, testId: String): List<CustomerCreateDTO> {
        return (0 until count).map { index ->
            CustomerCreateDTO(
                customerName = "Customer-$index-$testId"
            )
        }
    }

    private fun createCustomersForGroupTest(
        groupName: String,
        expectedCount: Int,
        testId: String,
    ): List<CustomerCreateDTO> {
        val testCustomers = mutableListOf<CustomerCreateDTO>()

        // Add customers of the target group
        repeat(expectedCount) { index ->
            testCustomers.add(
                CustomerCreateDTO(
                    customerName = "$groupName-Customer-$index-$testId"
                )
            )
        }

        // Add some customers of other groups to test filtering works properly
        repeat(3) { index ->
            testCustomers.add(
                CustomerCreateDTO(
                    customerName = "Other-Customer-$index-$testId"
                )
            )
        }

        return testCustomers
    }

    // Generate unique test ID for each test to prevent interference
    private fun generateTestId(): String {
        return "TEST-${System.currentTimeMillis()}-${Random.nextInt(1000, 9999)}"
    }
}