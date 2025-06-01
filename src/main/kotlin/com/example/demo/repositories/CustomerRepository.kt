package com.example.demo.repositories

import com.example.demo.domain.Customer
import com.example.demo.domain._CustomerDef
import com.example.demo.domain.customer
import com.example.demo.model.CustomerUpdateDTO
import com.example.demo.repositories.base.BaseRepository
import kotlinx.coroutines.flow.Flow
import org.komapper.core.dsl.Meta
import org.komapper.core.dsl.QueryDsl
import org.komapper.core.dsl.query.RelationUpdateQuery
import org.komapper.core.dsl.query.RelationUpdateReturningQuery
import org.komapper.r2dbc.R2dbcDatabase
import org.komapper.tx.core.TransactionProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Repository

@Repository
class CustomerRepository(
    @Autowired database: R2dbcDatabase,
) : BaseRepository<Customer, CustomerUpdateDTO, Long, _CustomerDef>( // Pass Long for ID
    database = database,
    entityDef = Meta.customer
) {
    override fun buildUpdateQuery(updateDTO: CustomerUpdateDTO): RelationUpdateQuery<Customer, Long, _CustomerDef> {
        return QueryDsl.update(entityDef)
            .set {
                updateDTO.customerName?.run { entityDef.customerName eq this }
            }
            .where { entityDef.id eq updateDTO.id }
    }

    override fun buildUpdateAndReturnQuery(updateDTO: CustomerUpdateDTO): RelationUpdateReturningQuery<List<Customer>> {
        return QueryDsl.update(entityDef)
            .set {
                updateDTO.customerName?.run { entityDef.customerName eq this }
            }
            .where { entityDef.id eq updateDTO.id }.returning()
    }

    suspend fun getEntities(
        page: Int = 1,
        size: Int = 1000,
        ids: List<Long>? = null,
        customerName: String? = null,
        customerNameContains: String? = null,
    ): Flow<Customer> {
        return database.withTransaction(
            transactionProperty = TransactionProperty.ReadOnly(true)
        ) {
            database.flowQuery {
                QueryDsl.from(entityDef)
                    .where {
                        ids?.let { entityDef.id inList it }
                        customerName?.let { entityDef.customerName eq it }
                        customerNameContains?.let {
                            it.split(" ").filter { keyword -> keyword.isNotBlank() }.forEach { keyword ->
                                entityDef.customerName contains keyword
                            }
                        }
                    }
                    .offset((page - 1).times(size)).limit(size)
            }
        }
    }

    override fun idProperty() = entityDef.id
}