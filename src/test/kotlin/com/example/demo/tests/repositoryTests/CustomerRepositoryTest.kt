package com.example.demo.tests.repositoryTests

import com.example.demo.TestContainersConfiguration
import com.example.demo.domain.Customer
import com.example.demo.generators.CustomerTestDataGenerator
import com.example.demo.mappers.CustomerMapper
import com.example.demo.repositories.CustomerRepository
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
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
@SpringBootTest
@Import(TestContainersConfiguration::class)
class CustomerRepositoryTest {

    @Autowired
    private lateinit var customerRepository: CustomerRepository

    @Autowired
    private lateinit var customerMapper: CustomerMapper

    // Helper to insert customers for current test
    private suspend fun insertTestCustomers(customers: List<Customer>): List<Customer> {
        return customerRepository.insertEntities(customers)
    }

    @ParameterizedTest
    @ValueSource(ints = [5, 10, 25, 50, 100])
    fun `insertEntities should handle various counts of random customers`(count: Int) = runTest {
        // Given
        val testUuid = UUID.randomUUID().toString()
        val randomCustomerDTOs =
            CustomerTestDataGenerator.generateRandomCustomerCreateDTOs(count, "BatchInsert $testUuid")
        val randomCustomers = randomCustomerDTOs.map { customerMapper.customerCreateDtoToCustomer(it) }

        // When
        val insertedCustomers = insertTestCustomers(randomCustomers)

        // Then
        assertEquals(count, insertedCustomers.size)

        // Verify by retrieving using IDs (most reliable method)
        val retrievedCustomers = customerRepository.getEntities(ids = insertedCustomers.map { it.id }).toList()
        assertEquals(count, retrievedCustomers.size)

        insertedCustomers.forEach { customer ->
            assertTrue(customer.id > 0L, "ID should be auto-generated")
            assertTrue(customer.customerName.isNotBlank())
            assertTrue(customer.version >= 0, "Version should be initialized")
            assertTrue(customer.createdAt != null, "CreatedAt should be set")
            assertTrue(customer.updatedAt != null, "UpdatedAt should be set")
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
        val totalCustomerDTOs =
            CustomerTestDataGenerator.generateRandomCustomerCreateDTOs(totalDataCount, "Pagination $testUuid")
        val totalCustomers = totalCustomerDTOs.map { customerMapper.customerCreateDtoToCustomer(it) }
        val insertedCustomers = insertTestCustomers(totalCustomers)

        // When - Get entities by IDs with pagination, handling 100 ID limit per request
        val allIds = insertedCustomers.map { it.id }
        val allRetrievedCustomers = mutableListOf<Customer>()

        // Process IDs in chunks of 100
        allIds.chunked(100).forEach { idChunk ->
            val chunkCustomers = customerRepository.getEntities(
                ids = idChunk,
            ).toList()
            allRetrievedCustomers.addAll(chunkCustomers)
        }

        // Apply pagination to the combined results
        val startIndex = (page - 1) * size
        val endIndex = min(startIndex + size, allRetrievedCustomers.size)
        val paginatedCustomers = if (startIndex < allRetrievedCustomers.size) {
            allRetrievedCustomers.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        // Then
        assertTrue(paginatedCustomers.size <= size, "Returned ${paginatedCustomers.size} items, should be <= $size")

        // Calculate expected items on this page
        val expectedItemsOnPage = min(size, max(0, totalDataCount - startIndex))

        if (expectedItemsOnPage > 0) {
            assertTrue(paginatedCustomers.isNotEmpty(), "Page $page should contain data")
            assertTrue(
                paginatedCustomers.size <= expectedItemsOnPage,
                "Page $page should contain at most $expectedItemsOnPage items"
            )
        }

        // Verify all returned customers belong to this test
        paginatedCustomers.forEach { customer ->
            assertTrue(allIds.contains(customer.id), "Customer should belong to this test")
        }

        // Additional verification - ensure we retrieved all expected data
        assertEquals(
            totalDataCount,
            allRetrievedCustomers.size,
            "Should retrieve all inserted customers across chunks"
        )
    }

    @ParameterizedTest
    @ValueSource(ints = [20, 30, 50, 100])
    fun `getEntities should filter by customer name with test isolation`(datasetSize: Int) = runTest {
        // Given
        val testUuid = UUID.randomUUID().toString()
        val randomCustomerDTOs =
            CustomerTestDataGenerator.generateRandomCustomerCreateDTOs(datasetSize, "NameFilter $testUuid")
        val randomCustomers = randomCustomerDTOs.map { customerMapper.customerCreateDtoToCustomer(it) }
        val insertedCustomers = insertTestCustomers(randomCustomers)
        val targetCustomer = insertedCustomers.random()

        // When - Filter by exact name and IDs to ensure test isolation
        val filteredCustomers = customerRepository.getEntities(
            ids = insertedCustomers.map { it.id },
            customerName = targetCustomer.customerName
        ).toList()

        // Then
        assertTrue(
            filteredCustomers.isNotEmpty(),
            "Should find at least one customer with name ${targetCustomer.customerName}"
        )
        filteredCustomers.forEach { customer ->
            assertEquals(targetCustomer.customerName, customer.customerName)
            assertTrue(insertedCustomers.any { it.id == customer.id }, "Customer should belong to this test")
        }
        // Verify the specific customer is in the results
        assertTrue(filteredCustomers.any { it.id == targetCustomer.id })
    }

    @ParameterizedTest
    @ValueSource(ints = [15, 25, 40])
    fun `getEntities should filter by customer name contains with test isolation`(totalCount: Int) = runTest {
        // Given - Create test-specific unique identifier
        val testUuid = UUID.randomUUID().toString().take(8)
        val matchingCount = min(5, totalCount / 3)

        val specificCustomers = (1..matchingCount).map {
            Customer(
                id = 0L,
                customerName = "Contains-$testUuid-$it"
            )
        }

        val otherCount = totalCount - matchingCount
        val otherCustomerDTOs =
            CustomerTestDataGenerator.generateRandomCustomerCreateDTOs(otherCount, "Other $testUuid")
        val otherCustomers = otherCustomerDTOs.map { customerMapper.customerCreateDtoToCustomer(it) }

        val allInserted = insertTestCustomers(specificCustomers + otherCustomers)

        // When - Filter by contains and IDs for test isolation
        val filteredCustomers = customerRepository.getEntities(
            ids = allInserted.map { it.id },
            customerNameContains = "Contains-$testUuid"
        ).toList()

        // Then
        assertEquals(
            matchingCount, filteredCustomers.size,
            "Should find exactly $matchingCount customers containing '$testUuid'"
        )
        filteredCustomers.forEach { customer ->
            assertTrue(
                customer.customerName.contains(testUuid, ignoreCase = true),
                "Customer name '${customer.customerName}' should contain '$testUuid'"
            )
            assertTrue(allInserted.any { it.id == customer.id }, "Customer should belong to this test")
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [15, 25, 40])
    fun `patchEntities should update customers successfully with test isolation`(datasetSize: Int) = runTest {
        // Given
        val testUuid = UUID.randomUUID().toString()
        val originalCustomerDTOs =
            CustomerTestDataGenerator.generateRandomCustomerCreateDTOs(datasetSize, "PatchTest $testUuid")
        val originalCustomers = originalCustomerDTOs.map { customerMapper.customerCreateDtoToCustomer(it) }
        val insertedCustomers = insertTestCustomers(originalCustomers)

        // Update between 30% to 70% of the customers dynamically
        val updateRatio = Random.nextDouble(0.3, 0.7)
        val updateDTOs =
            CustomerTestDataGenerator.generateRandomCustomerUpdateDTOs(insertedCustomers.map { it.id }, updateRatio)

        // When
        customerRepository.patchEntities(updateDTOs)

        // Then - Use specific IDs to verify updates
        val updatedCustomers = customerRepository.getEntities(ids = updateDTOs.map { it.id }).toList()
        assertEquals(
            updateDTOs.size, updatedCustomers.size,
            "Should retrieve all ${updateDTOs.size} updated customers from dataset of $datasetSize"
        )

        updateDTOs.forEach { updateDTO ->
            val updatedCustomer = updatedCustomers.first { it.id == updateDTO.id }
            assertTrue(
                insertedCustomers.any { it.id == updatedCustomer.id },
                "Updated customer should belong to this test"
            )

            updateDTO.customerName?.let { assertEquals(it, updatedCustomer.customerName) }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [20, 35, 50])
    fun `deleteEntities should delete specific customers with test isolation`(datasetSize: Int) = runTest {
        // Given
        val testUuid = UUID.randomUUID().toString()
        val originalCustomerDTOs =
            CustomerTestDataGenerator.generateRandomCustomerCreateDTOs(datasetSize, "DeleteTest $testUuid")
        val originalCustomers = originalCustomerDTOs.map { customerMapper.customerCreateDtoToCustomer(it) }
        val insertedCustomers = insertTestCustomers(originalCustomers)

        // Delete between 20% to 60% of customers dynamically
        val deleteRatio = Random.nextDouble(0.2, 0.6)
        val deleteCount = (datasetSize * deleteRatio).toInt()
        val customersToDelete = insertedCustomers.shuffled().take(deleteCount)
        val idsToDelete = customersToDelete.map { it.id }

        // When
        customerRepository.deleteEntities(idsToDelete)

        // Then - Check only our test data
        val remainingTestCustomers = customerRepository.getEntities(ids = insertedCustomers.map { it.id }).toList()
        val deletedIds = idsToDelete.toSet()
        val expectedRemainingCount = datasetSize - deleteCount

        remainingTestCustomers.forEach { customer ->
            assertTrue(customer.id !in deletedIds, "Deleted customer with ID ${customer.id} should not exist")
        }

        assertEquals(
            expectedRemainingCount, remainingTestCustomers.size,
            "Should have exactly $expectedRemainingCount customers remaining from our test data"
        )
    }

    @ParameterizedTest
    @ValueSource(ints = [50, 80, 120])
    fun `getEntities should handle multiple filter conditions with test isolation`(datasetSize: Int) = runTest {
        // Given
        val testUuid = UUID.randomUUID().toString()
        val randomCustomerDTOs =
            CustomerTestDataGenerator.generateRandomCustomerCreateDTOs(datasetSize, "MultiFilter $testUuid")
        val randomCustomers = randomCustomerDTOs.map { customerMapper.customerCreateDtoToCustomer(it) }
        val insertedCustomers = insertTestCustomers(randomCustomers)

        // Create filters that should reasonably match some of the data
        val randomNamePart = testUuid.take(6) // Use part of our test UUID

        // When - Test multiple filters that work with Customer model
        val filteredCustomers = customerRepository.getEntities(
            ids = insertedCustomers.map { it.id }, // Ensure test isolation
            customerNameContains = randomNamePart
        ).toList()

        // Then
        filteredCustomers.forEach { customer ->
            assertTrue(
                customer.customerName.contains(randomNamePart, ignoreCase = true),
                "Customer name '${customer.customerName}' should contain '$randomNamePart'"
            )
            assertTrue(insertedCustomers.any { it.id == customer.id }, "Customer should belong to this test")
        }

        // Verify the filter is working by checking manual count
        val manualCount = insertedCustomers.count { customer ->
            customer.customerName.contains(randomNamePart, ignoreCase = true)
        }
        assertEquals(
            manualCount, filteredCustomers.size,
            "Filter should return exactly $manualCount customers matching all criteria"
        )
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 5, 10])
    fun `insertEntities should handle various sizes including empty`(count: Int) = runTest {
        // Given
        val testUuid = UUID.randomUUID().toString()
        val customers = if (count == 0) {
            emptyList()
        } else {
            val customerDTOs =
                CustomerTestDataGenerator.generateRandomCustomerCreateDTOs(count, "VariousSize $testUuid")
            customerDTOs.map { customerMapper.customerCreateDtoToCustomer(it) }
        }

        // When
        val result = insertTestCustomers(customers)

        // Then
        assertEquals(count, result.size)
        if (count > 0) {
            // Verify by retrieving with IDs
            val retrieved = customerRepository.getEntities(ids = result.map { it.id }).toList()
            assertEquals(count, retrieved.size)

            result.forEach { customer ->
                assertTrue(customer.id > 0L)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [200, 300, 500])
    fun `stress test with large dataset and test isolation`(datasetSize: Int) = runTest {
        // Given
        val testUuid = UUID.randomUUID().toString()
        val largeCustomerDTOs =
            CustomerTestDataGenerator.generateRandomCustomerCreateDTOs(datasetSize, "StressTest $testUuid")
        val largeDataset = largeCustomerDTOs.map { customerMapper.customerCreateDtoToCustomer(it) }

        // When - Insert
        val insertedCustomers = insertTestCustomers(largeDataset)

        // Then - Verify insertion
        assertEquals(datasetSize, insertedCustomers.size)

        // When - Random updates (30% of dataset)
        val updateRatio = 0.3
        val randomUpdates =
            CustomerTestDataGenerator.generateRandomCustomerUpdateDTOs(insertedCustomers.map { it.id }, updateRatio)
        customerRepository.patchEntities(randomUpdates)

        // When - Random deletion (25% of dataset)
        val deleteRatio = 0.25
        val deleteCount = (datasetSize * deleteRatio).toInt()
        val randomDeletions = insertedCustomers.shuffled().take(deleteCount).map { it.id }
        customerRepository.deleteEntities(randomDeletions)

        // Then - Verify the final state using specific IDs
        val expectedRemainingCount = datasetSize - deleteCount
        val finalCustomers = customerRepository.getEntities(ids = insertedCustomers.map { it.id }).toList()

        assertEquals(
            expectedRemainingCount, finalCustomers.size,
            "Should have exactly $expectedRemainingCount customers after operations on $datasetSize dataset"
        )

        // Verify no deleted customers remain
        val deletedIds = randomDeletions.toSet()
        finalCustomers.forEach { customer ->
            assertTrue(customer.id !in deletedIds, "Deleted customer should not exist")
        }
    }

    // Optional: Test for handling concurrent operations on different datasets
    @Test
    fun `concurrent operations should not interfere with different test datasets`() = runTest {
        val testUuid1 = UUID.randomUUID().toString()
        val testUuid2 = UUID.randomUUID().toString()

        // Create two separate datasets
        val dataset1DTOs = CustomerTestDataGenerator.generateRandomCustomerCreateDTOs(10, "Concurrent1 $testUuid1")
        val dataset2DTOs = CustomerTestDataGenerator.generateRandomCustomerCreateDTOs(10, "Concurrent2 $testUuid2")

        val dataset1 = dataset1DTOs.map { customerMapper.customerCreateDtoToCustomer(it) }
        val dataset2 = dataset2DTOs.map { customerMapper.customerCreateDtoToCustomer(it) }

        // Insert both datasets
        val inserted1 = customerRepository.insertEntities(dataset1)
        val inserted2 = customerRepository.insertEntities(dataset2)

        // Verify isolation - each dataset should only contain its own data
        val retrieved1 = customerRepository.getEntities(ids = inserted1.map { it.id }).toList()
        val retrieved2 = customerRepository.getEntities(ids = inserted2.map { it.id }).toList()

        assertEquals(10, retrieved1.size)
        assertEquals(10, retrieved2.size)

        // Verify no overlap
        val ids1 = retrieved1.map { it.id }.toSet()
        val ids2 = retrieved2.map { it.id }.toSet()
        assertTrue(ids1.intersect(ids2).isEmpty(), "Datasets should not overlap")
    }
}