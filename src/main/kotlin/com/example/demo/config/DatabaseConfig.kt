import com.example.demo.properties.DatabaseProperties
import com.example.demo.properties.InitialDatabaseProperties
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.Option
import org.komapper.r2dbc.R2dbcDatabase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class DatabaseConfig(
    private val databaseProperties: DatabaseProperties,
    private val initialDatabaseProperties: InitialDatabaseProperties,
) {

    @Bean
    @Primary
    fun writeDatabase(): R2dbcDatabase {
        val dbProps = databaseProperties.write
        val databaseName = dbProps.name

        val options = ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "pool")
            .option(ConnectionFactoryOptions.PROTOCOL, dbProps.protocol)
            .option(ConnectionFactoryOptions.DATABASE, databaseName)
            .option(ConnectionFactoryOptions.HOST, dbProps.host)
            .option(ConnectionFactoryOptions.PORT, dbProps.port)
            .option(ConnectionFactoryOptions.USER, dbProps.username)
            .option(ConnectionFactoryOptions.PASSWORD, dbProps.password)
            .option(Option.valueOf("schema"), dbProps.schema)
            .option(Option.valueOf("initialSize"), dbProps.pool.initialSize)
            .option(Option.valueOf("minIdle"), dbProps.pool.minIdle)
            .option(Option.valueOf("maxIdleTime"), dbProps.pool.maxIdleTime)
            .build()

        return R2dbcDatabase(options)
    }

    @Bean
    fun readDatabase(): R2dbcDatabase {
        val dbProps = databaseProperties.read
        val databaseName = dbProps.name

        val options = ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "pool")
            .option(ConnectionFactoryOptions.PROTOCOL, dbProps.protocol)
            .option(ConnectionFactoryOptions.DATABASE, databaseName)
            .option(ConnectionFactoryOptions.HOST, dbProps.host)
            .option(ConnectionFactoryOptions.PORT, dbProps.port)
            .option(ConnectionFactoryOptions.USER, dbProps.username)
            .option(ConnectionFactoryOptions.PASSWORD, dbProps.password)
            .option(Option.valueOf("schema"), dbProps.schema)
            .option(Option.valueOf("initialSize"), dbProps.pool.initialSize)
            .option(Option.valueOf("minIdle"), dbProps.pool.minIdle)
            .option(Option.valueOf("maxIdleTime"), dbProps.pool.maxIdleTime)
            .build()

        return R2dbcDatabase(options)
    }

    @Bean
    fun initialR2dbcDatabase(): R2dbcDatabase {
        val options = ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, initialDatabaseProperties.protocol)
            .option(ConnectionFactoryOptions.HOST, initialDatabaseProperties.host)
            .option(ConnectionFactoryOptions.PORT, initialDatabaseProperties.port)
            .option(ConnectionFactoryOptions.USER, initialDatabaseProperties.username)
            .option(ConnectionFactoryOptions.PASSWORD, initialDatabaseProperties.password)
            .build()

        return R2dbcDatabase(options)
    }
}