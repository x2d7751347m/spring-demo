package com.example.demo.repositories.base

import org.komapper.core.dsl.QueryDsl
import org.komapper.core.dsl.metamodel.EntityMetamodel
import org.komapper.core.dsl.metamodel.PropertyMetamodel
import org.komapper.core.dsl.query.RelationUpdateQuery
import org.komapper.core.dsl.query.RelationUpdateReturningQuery
import org.komapper.r2dbc.R2dbcDatabase
import org.komapper.tx.core.TransactionProperty

// Base interface for Update DTOs
interface BaseIdDTO {
    val id: Long
}

// Generic Base Repository class
// Constrain ENTITY_DEF to be an EntityMetamodel
abstract class BaseRepository<ENTITY : Any, UPDATE_DTO : BaseIdDTO, ID : Any, ENTITY_DEF : EntityMetamodel<ENTITY, ID, ENTITY_DEF>>(
    protected open val database: R2dbcDatabase,
    protected open val entityDef: ENTITY_DEF,
) {
    /**
     * Inserts multiple entities into the database.
     * @param entities The list of entities to insert.
     * @return The list of inserted entities.
     */
    suspend fun insertEntities(entities: List<ENTITY>): List<ENTITY> {
        return database.runQuery {
            QueryDsl.insert(entityDef).multiple(entities)
        }
    }

    /**
     * Partially updates multiple entities in a single transaction.
     * @param updateDTOs The list of DTOs containing update information.
     * @return The list of updated entities.
     */
    suspend fun patchEntities(updateDTOs: List<UPDATE_DTO>) {
        if (updateDTOs.isEmpty()) {
            return
        }
        return database.withTransaction(
            transactionProperty = TransactionProperty.IsolationLevel.READ_COMMITTED
        ) {
            for (updateDTO in updateDTOs) {
                database.runQuery {
                    buildUpdateQuery(updateDTO)
                }
            }
        }
    }

    /**
     * Deletes multiple entities by their IDs in a single transaction.
     * @param ids The list of IDs from entities to delete.
     */
    suspend fun deleteEntities(ids: List<Long>) =
        database.withTransaction(
            transactionProperty = TransactionProperty.IsolationLevel.SERIALIZABLE
        ) {
            database.runQuery {
                QueryDsl.delete(entityDef).where {
                    idProperty() inList ids
                }
            }
        }

    // Abstract methods to be implemented by each concrete Repository
    /**
     * Builds an update query for a single entity based on the provided DTO.
     * @param updateDTO The DTO containing the update information.
     * @return A [RelationUpdateQuery] for the entity.
     */
    protected abstract fun buildUpdateQuery(updateDTO: UPDATE_DTO): RelationUpdateQuery<ENTITY, ID, ENTITY_DEF>

    protected abstract fun buildUpdateAndReturnQuery(updateDTO: UPDATE_DTO): RelationUpdateReturningQuery<List<ENTITY>>

    /**
     * Builds a condition to check if an entity's ID is within a given list of IDs.
     * @param ids The list of IDs.
     * @return A Komapper condition.
     */
    // This can now be made non-abstract if you are sure all your entities have `id: Long`
    // protected fun buildIdInListCondition(ids: List<Long>) = entityDef.id inList ids
    protected abstract fun idProperty(): PropertyMetamodel<ENTITY, Long, Long>
}