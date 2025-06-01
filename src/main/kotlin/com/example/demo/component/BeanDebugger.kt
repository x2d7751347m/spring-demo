package com.example.demo.component

import org.komapper.r2dbc.R2dbcDatabase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.ApplicationContext

//@Component
class BeanDebugger : ApplicationRunner {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    override fun run(args: ApplicationArguments?) {
        println("=== R2dbcDatabase Beans ===")
        applicationContext.getBeansOfType(R2dbcDatabase::class.java).forEach { name, bean ->
            println("Bean name: $name")
        }

        println("=== All Bean Names ===")
        applicationContext.beanDefinitionNames.filter {
            it.contains("database", ignoreCase = true)
        }.forEach {
            println("Database related bean: $it")
        }
    }
}