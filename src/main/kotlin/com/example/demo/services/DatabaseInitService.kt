package com.example.demo.services

import com.example.demo.domain.beer
import com.example.demo.domain.customer
import com.example.demo.properties.DatabaseProperties
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.R2dbcBadGrammarException
import io.r2dbc.spi.R2dbcNonTransientResourceException
import org.komapper.core.dsl.Meta
import org.komapper.core.dsl.QueryDsl
import org.komapper.r2dbc.R2dbcDatabase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DatabaseInitService(
//    @Qualifier("initialR2dbcDatabase") private val initialDatabase: R2dbcDatabase,
//    @Qualifier("writeDatabase")
    private val writeDatabase: R2dbcDatabase,
    private val databaseProperties: DatabaseProperties,
) {
    private val logger = LoggerFactory.getLogger(DatabaseInitService::class.java)

    suspend fun dropDatabase() {
        logger.info("Starting database drop...")

        val databaseName = databaseProperties.write.name

        val initialDatabase: R2dbcDatabase by lazy {
            val options = ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, databaseProperties.write.protocol)
                .option(ConnectionFactoryOptions.HOST, databaseProperties.write.host)
                .option(ConnectionFactoryOptions.PORT, databaseProperties.write.port)
                .option(ConnectionFactoryOptions.USER, databaseProperties.write.username)
                .option(ConnectionFactoryOptions.PASSWORD, databaseProperties.write.password)
                .build()

            R2dbcDatabase(options)
        }

        try {
            initialDatabase.runQuery {
                QueryDsl.executeScript("DROP DATABASE \"$databaseName\";")
            }
            logger.info("Dropped existing database: $databaseName")
        } catch (_: R2dbcNonTransientResourceException) {
            logger.info("Database $databaseName does not exist, skipping drop")
        }
    }

    suspend fun initializeDatabase() {
        try {
            logger.info("Starting database initialize...")

            val databaseName = databaseProperties.write.name

            val initialDatabase: R2dbcDatabase by lazy {
                val options = ConnectionFactoryOptions.builder()
                    .option(ConnectionFactoryOptions.DRIVER, databaseProperties.write.protocol)
                    .option(ConnectionFactoryOptions.HOST, databaseProperties.write.host)
                    .option(ConnectionFactoryOptions.PORT, databaseProperties.write.port)
                    .option(ConnectionFactoryOptions.USER, databaseProperties.write.username)
                    .option(ConnectionFactoryOptions.PASSWORD, databaseProperties.write.password)
                    .build()

                R2dbcDatabase(options)
            }

            try {
                try {
                    initialDatabase.runQuery {
                        QueryDsl.executeScript("CREATE DATABASE \"$databaseName\";")
                    }
                    logger.info("Created new database: $databaseName")
                } catch (_: R2dbcBadGrammarException) {
                }

                createTables()

//                 modifyDb()

            } catch (e: R2dbcNonTransientResourceException) {
                logger.error("Failed to create database or tables", e)
                throw e
            }

            logger.info("Database initialize completed successfully")
        } catch (e: Exception) {
            logger.error("Failed to initialize database", e)
            throw e
        }
    }

    suspend fun createTables() {
        logger.info("Creating database tables...")

        val tableDefinitions = listOf(
            Meta.beer,
            Meta.customer,
        )

        for (tableDef in tableDefinitions) {
            try {
                writeDatabase.runQuery {
                    QueryDsl.create(tableDef)
                }
                logger.debug("Created table: ${tableDef.tableName()}")
            } catch (e: Exception) {
                logger.error("Failed to create table: ${tableDef.tableName()}", e)
                throw e
            }
        }

        logger.info("All tables created successfully")
    }
}