package com.example.demo.services

import com.example.demo.properties.DatabaseProperties
import com.example.demo.properties.InitialDatabaseProperties
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.R2dbcNonTransientResourceException
import org.komapper.core.dsl.QueryDsl
import org.komapper.r2dbc.R2dbcDatabase
import org.slf4j.LoggerFactory

//@Service
class DatabaseInitService(
    private val database: R2dbcDatabase,
    private val databaseProperties: DatabaseProperties,
    private val initialDatabaseProperties: InitialDatabaseProperties,
) {
    private val logger = LoggerFactory.getLogger(DatabaseInitService::class.java)

    suspend fun dropDatabase() {
        logger.info("Starting database drop...")

        val databaseName = databaseProperties.write.name

        val initialDatabase: R2dbcDatabase by lazy {
            val options = ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, initialDatabaseProperties.protocol)
                .option(ConnectionFactoryOptions.HOST, initialDatabaseProperties.host)
                .option(ConnectionFactoryOptions.PORT, initialDatabaseProperties.port)
                .option(ConnectionFactoryOptions.USER, initialDatabaseProperties.username)
                .option(ConnectionFactoryOptions.PASSWORD, initialDatabaseProperties.password)
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
}