package com.example.demo.repositories

import com.example.demo.domain.Customer
import com.example.demo.domain.customer
import com.example.demo.model.CustomerUpdateDTO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.last
import org.komapper.core.dsl.Meta
import org.komapper.core.dsl.QueryDsl
import org.komapper.core.dsl.operator.count
import org.komapper.r2dbc.R2dbcDatabase
import org.komapper.tx.core.TransactionProperty
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
class CustomerRepository(
    db: R2dbcDatabase,
) {
    val r2dbcWriteDatabase = db
    val r2dbcReadDatabase = db
    val customerDef = Meta.customer
    suspend fun insertCustomer(
        customer: Customer,
    ): Customer {
        return r2dbcWriteDatabase.runQuery {
            QueryDsl.insert(customerDef).single(customer)
        }
    }

    suspend fun updateCustomer(
        customerDTO: CustomerUpdateDTO,
    ): Customer {
        r2dbcWriteDatabase.withTransaction(transactionProperty = TransactionProperty.IsolationLevel.READ_COMMITTED) {
            r2dbcWriteDatabase.runQuery {
                QueryDsl.update(customerDef)
                    .set {
                        customerDTO.customerName?.run { customerDef.customerName eq this }
                    }
                    .where { customerDef.id eq customerDTO.id }
            }
        }
        return r2dbcWriteDatabase.flowQuery {
            QueryDsl.from(customerDef).where { customerDef.id eq customerDTO.id }
        }.last()
    }

    suspend fun fetchCustomers(
        page: Int = 1,
        size: Int = 100,
        id: Long? = null,
        customerName: String? = null,
        customerNameContains: String? = null,
        createdAtBefore: LocalDateTime? = null,
        createdAtAfter: LocalDateTime? = null,
        updatedAtBefore: LocalDateTime? = null,
        updatedAtAfter: LocalDateTime? = null,
    ): Flow<Customer> =
        r2dbcReadDatabase.withTransaction(transactionProperty = TransactionProperty.ReadOnly(true)) {
            r2dbcReadDatabase.flowQuery {
                QueryDsl.from(customerDef)
                    .where {
                        id?.let { customerDef.id eq it }
                        customerName?.let { customerDef.customerName eq it }
                        customerNameContains?.let {
                            it.split(" ").forEach {
                                customerDef.customerName contains it
                            }
                        }
                        createdAtBefore?.let { customerDef.createdAt lessEq it }
                        createdAtAfter?.let { customerDef.createdAt greaterEq it }
                        updatedAtBefore?.let { customerDef.updatedAt lessEq it }
                        updatedAtAfter?.let { customerDef.updatedAt greaterEq it }
                    }
                    .offset((page - 1).times(size)).limit(size)
            }
        }

    suspend fun countTotalCustomers(
        id: Long? = null,
        customerName: String? = null,
        customerNameContains: String? = null,
        customerStyle: String? = null,
        customerStyleContains: String? = null,
        upc: String? = null,
        upcContains: String? = null,
        quantityOnHand: Int? = null,
        price: BigDecimal? = null,
        createdAtBefore: LocalDateTime? = null,
        createdAtAfter: LocalDateTime? = null,
        updatedAtBefore: LocalDateTime? = null,
        updatedAtAfter: LocalDateTime? = null,
    ): Long =
        r2dbcReadDatabase.withTransaction(transactionProperty = TransactionProperty.ReadOnly(true)) {
            r2dbcReadDatabase.runQuery {
                QueryDsl.from(customerDef)
                    .where {
                        id?.let { customerDef.id eq it }
                        customerName?.let { customerDef.customerName eq it }
                        customerNameContains?.let {
                            it.split(" ").forEach {
                                customerDef.customerName contains it
                            }
                        }
                        createdAtBefore?.let { customerDef.createdAt lessEq it }
                        createdAtAfter?.let { customerDef.createdAt greaterEq it }
                        updatedAtBefore?.let { customerDef.updatedAt lessEq it }
                        updatedAtAfter?.let { customerDef.updatedAt greaterEq it }
                    }.select(count())
            }!!
        }

    suspend fun deleteCustomer(
        id: Long,
    ) =
        r2dbcWriteDatabase.withTransaction(transactionProperty = TransactionProperty.IsolationLevel.SERIALIZABLE) {
            r2dbcWriteDatabase.runQuery {
                QueryDsl.delete(
                    customerDef
                ).where { customerDef.id eq id }
            }
        }
}