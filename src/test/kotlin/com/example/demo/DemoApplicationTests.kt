package com.example.demo

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@Import(TestContainersConfiguration::class)
@SpringBootTest
class DemoApplicationTests {

    @Test
    fun contextLoads() {
        println("\nprint: contextLoads\n")
    }
}