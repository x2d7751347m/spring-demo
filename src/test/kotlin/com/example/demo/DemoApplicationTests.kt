package com.example.demo

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

//@Import(TestcontainersConfiguration::class)
@SpringBootTest
class DemoApplicationTests {

    @Test
    fun contextLoads() {
        println("\nprint: Test Output\n")
    }
}