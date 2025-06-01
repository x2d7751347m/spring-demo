package com.example.demo.repositories

import com.example.demo.domain.Beer
import com.example.demo.domain._BeerDef
import com.example.demo.domain.beer
import com.example.demo.model.BeerUpdateDTO
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
import java.math.BigDecimal


@Repository
class BeerRepository(
    @Autowired database: R2dbcDatabase,
) : BaseRepository<Beer, BeerUpdateDTO, Long, _BeerDef>( // Pass Long for ID
    database = database,
    entityDef = Meta.beer
) {

    override fun buildUpdateQuery(updateDTO: BeerUpdateDTO): RelationUpdateQuery<Beer, Long, _BeerDef> {
        return QueryDsl.update(entityDef)
            .set {
                updateDTO.beerName?.run { entityDef.beerName eq this }
                updateDTO.beerStyle?.run { entityDef.beerStyle eq this }
                updateDTO.upc?.run { entityDef.upc eq this }
                updateDTO.quantityOnHand?.run { entityDef.quantityOnHand eq this }
                updateDTO.price?.run { entityDef.price eq this }
            }
            .where { entityDef.id eq updateDTO.id }
    }

    override fun buildUpdateAndReturnQuery(updateDTO: BeerUpdateDTO): RelationUpdateReturningQuery<List<Beer>> {
        return QueryDsl.update(entityDef)
            .set {
                updateDTO.beerName?.run { entityDef.beerName eq this }
                updateDTO.beerStyle?.run { entityDef.beerStyle eq this }
                updateDTO.upc?.run { entityDef.upc eq this }
                updateDTO.quantityOnHand?.run { entityDef.quantityOnHand eq this }
                updateDTO.price?.run { entityDef.price eq this }
            }
            .where { entityDef.id eq updateDTO.id }.returning()
    }

    override fun idProperty() = entityDef.id


    suspend fun getEntities(
        page: Int = 1,
        size: Int = 1000,
        ids: List<Long>? = null,
        beerName: String? = null,
        beerNameContains: String? = null,
        beerStyle: String? = null,
        beerStyleContains: String? = null,
        upc: String? = null,
        quantityOnHand: Int? = null,
        minPrice: BigDecimal? = null,
        maxPrice: BigDecimal? = null,
    ): Flow<Beer> {
        return database.withTransaction(
            transactionProperty = TransactionProperty.ReadOnly(true)
        ) {
            database.flowQuery {
                QueryDsl.from(entityDef)
                    .where {
                        ids?.let { entityDef.id inList it }
                        beerName?.let { entityDef.beerName eq it }
                        beerNameContains?.let {
                            it.split(" ").filter { keyword -> keyword.isNotBlank() }.forEach { keyword ->
                                entityDef.beerName contains keyword
                            }
                        }
                        beerStyle?.let { entityDef.beerStyle eq it }
                        beerStyleContains?.let {
                            it.split(" ").filter { keyword -> keyword.isNotBlank() }.forEach { keyword ->
                                entityDef.beerStyle contains keyword
                            }
                        }
                        upc?.let { entityDef.upc eq it }
                        quantityOnHand?.let { entityDef.quantityOnHand eq it }
                        minPrice?.let { entityDef.price greaterEq it }
                        maxPrice?.let { entityDef.price lessEq it }
                    }
                    .offset((page - 1).times(size)).limit(size)
            }
        }
    }
}