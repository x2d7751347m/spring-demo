package com.example.demo.repositories

import com.example.demo.domain.Beer
import com.example.demo.domain.beer
import com.example.demo.model.BeerUpdateDTO
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
class BeerRepository(
    db: R2dbcDatabase,
) {
    val r2dbcWriteDatabase = db
    val r2dbcReadDatabase = db
    val beerDef = Meta.beer
    suspend fun insertBeer(
        beer: Beer,
    ): Beer {
        return r2dbcWriteDatabase.runQuery {
            QueryDsl.insert(beerDef).single(beer)
        }
    }

    suspend fun updateBeer(
        beerDTO: BeerUpdateDTO,
    ): Beer {
        r2dbcWriteDatabase.withTransaction(transactionProperty = TransactionProperty.IsolationLevel.READ_COMMITTED) {
            r2dbcWriteDatabase.runQuery {
                QueryDsl.update(beerDef)
                    .set {
                        beerDTO.beerName?.run { beerDef.beerName eq this }
                        beerDTO.beerStyle?.run { beerDef.beerStyle eq this }
                        beerDTO.upc?.run { beerDef.upc eq this }
                        beerDTO.quantityOnHand?.run { beerDef.quantityOnHand eq this }
                        beerDTO.price?.run { beerDef.price eq this }
                    }
                    .where { beerDef.id eq beerDTO.id }
            }
        }
        return r2dbcWriteDatabase.flowQuery {
            QueryDsl.from(beerDef).where { beerDef.id eq beerDTO.id }
        }.last()
    }

    suspend fun fetchBeers(
        page: Int = 1,
        size: Int = 100,
        id: Long? = null,
        beerName: String? = null,
        beerNameContains: String? = null,
        beerStyle: String? = null,
        beerStyleContains: String? = null,
        upc: String? = null,
        upcContains: String? = null,
        quantityOnHand: Int? = null,
        price: BigDecimal? = null,
        createdAtBefore: LocalDateTime? = null,
        createdAtAfter: LocalDateTime? = null,
        updatedAtBefore: LocalDateTime? = null,
        updatedAtAfter: LocalDateTime? = null,
    ): Flow<Beer> =
        r2dbcReadDatabase.withTransaction(transactionProperty = TransactionProperty.ReadOnly(true)) {
            r2dbcReadDatabase.flowQuery {
                QueryDsl.from(beerDef)
                    .where {
                        id?.let { beerDef.id eq it }
                        beerName?.let { beerDef.beerName eq it }
                        beerNameContains?.let {
                            it.split(" ").forEach {
                                beerDef.beerName contains it
                            }
                        }
                        beerStyle?.let { beerDef.beerStyle eq it }
                        beerStyleContains?.let {
                            it.split(" ").forEach {
                                beerDef.beerStyle contains it
                            }
                        }
                        upc?.let { beerDef.upc eq it }
                        upcContains?.let {
                            it.split(" ").forEach {
                                beerDef.upc contains it
                            }
                        }
                        quantityOnHand?.let { beerDef.quantityOnHand eq it }
                        price?.let { beerDef.price eq it }
                        createdAtBefore?.let { beerDef.createdAt lessEq it }
                        createdAtAfter?.let { beerDef.createdAt greaterEq it }
                        updatedAtBefore?.let { beerDef.updatedAt lessEq it }
                        updatedAtAfter?.let { beerDef.updatedAt greaterEq it }
                    }
                    .offset((page - 1).times(size)).limit(size)
            }
        }

    suspend fun countTotalBeers(
        id: Long? = null,
        beerName: String? = null,
        beerNameContains: String? = null,
        beerStyle: String? = null,
        beerStyleContains: String? = null,
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
                QueryDsl.from(beerDef)
                    .where {
                        id?.let { beerDef.id eq it }
                        beerName?.let { beerDef.beerName eq it }
                        beerNameContains?.let {
                            it.split(" ").forEach {
                                beerDef.beerName contains it
                            }
                        }
                        beerStyle?.let { beerDef.beerStyle eq it }
                        beerStyleContains?.let {
                            it.split(" ").forEach {
                                beerDef.beerStyle contains it
                            }
                        }
                        upc?.let { beerDef.upc eq it }
                        upcContains?.let {
                            it.split(" ").forEach {
                                beerDef.upc contains it
                            }
                        }
                        quantityOnHand?.let { beerDef.quantityOnHand eq it }
                        price?.let { beerDef.price eq it }
                        createdAtBefore?.let { beerDef.createdAt lessEq it }
                        createdAtAfter?.let { beerDef.createdAt greaterEq it }
                        updatedAtBefore?.let { beerDef.updatedAt lessEq it }
                        updatedAtAfter?.let { beerDef.updatedAt greaterEq it }
                    }.select(count())
            }!!
        }

    suspend fun deleteBeer(
        id: Long,
    ) =
        r2dbcWriteDatabase.withTransaction(transactionProperty = TransactionProperty.IsolationLevel.SERIALIZABLE) {
            r2dbcWriteDatabase.runQuery {
                QueryDsl.delete(
                    beerDef
                ).where { beerDef.id eq id }
            }
        }
}