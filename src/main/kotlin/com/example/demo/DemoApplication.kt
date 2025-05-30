package com.example.demo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication


@SpringBootApplication
@ConfigurationPropertiesScan
//@EnableAutoConfiguration(
//    excludeName = [
//        "org.komapper.spring.boot.autoconfigure.r2dbc.KomapperR2dbcAutoConfiguration"
//    ]
//)
class DemoApplication

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}