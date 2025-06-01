package com.example.demo

import org.springframework.boot.test.context.TestConfiguration

@TestConfiguration(proxyBeanMethods = true)
class TestContainersConfiguration {

//    @Bean
//    @ServiceConnection
//    fun postgresContainer(): PostgreSQLContainer<*> {
//        return PostgreSQLContainer(DockerImageName.parse("postgres:latest"))
//            .withDatabaseName("spring_demo_test_database")
//            .withUsername("test_username")
//            .withPassword("test_password")
//    }
}
