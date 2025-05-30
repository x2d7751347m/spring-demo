package com.example.demo.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration


@ConfigurationProperties(prefix = "database")
data class DatabaseProperties(
    val write: DatabaseConnectionProperties,
    val read: DatabaseConnectionProperties,
    val drop: Boolean = false,
    val initialize: Boolean = false,
)

data class DatabaseConnectionProperties(
    val protocol: String = "postgresql",
    val host: String = "localhost",
    val port: Int = 5432,
    val name: String,
    val username: String,
    val password: String,
    val schema: String,
    val pool: PoolProperties = PoolProperties(),
)

data class PoolProperties(
    val initialSize: Int = 5,
    val minIdle: Int = 5,
    val maxIdleTime: Duration = Duration.ofSeconds(30),
)

@ConfigurationProperties(prefix = "swagger")
data class SwaggerProperties(
    val host: String = "localhost",
)

@ConfigurationProperties(prefix = "database.initial")
data class InitialDatabaseProperties(
    val protocol: String = "postgresql",
    val host: String = "localhost",
    val port: Int = 5432,
    val username: String,
    val password: String,
)