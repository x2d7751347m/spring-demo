package com.example.demo.component

import com.example.demo.properties.DatabaseProperties
import com.example.demo.services.DatabaseInitService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import kotlin.system.exitProcess

//@Component
class DatabaseInitRunner(
    private val databaseProperties: DatabaseProperties,
    private val databaseInitService: DatabaseInitService,
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(DatabaseInitRunner::class.java)

    override fun run(args: ApplicationArguments) {
        if (databaseProperties.drop) {
            logger.info("Database drop is enabled, starting drop process...")

            runBlocking {
                try {
                    databaseInitService.dropDatabase()
                    exitProcess(0)
                } catch (e: Exception) {
                    logger.error("Database drop failed, application will continue", e)
                    throw e
//                     exitProcess(1)
                }
            }
        }
    }
}