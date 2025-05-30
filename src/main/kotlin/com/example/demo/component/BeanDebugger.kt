package com.example.demo.component

//@Component
//class BeanDebugger : ApplicationRunner {
//
//    @Autowired
//    private lateinit var applicationContext: ApplicationContext
//
//    override fun run(args: ApplicationArguments?) {
//        println("=== R2dbcDatabase Beans ===")
//        applicationContext.getBeansOfType(R2dbcDatabase::class.java).forEach { name, bean ->
//            println("Bean name: $name")
//        }
//
//        println("=== All Bean Names ===")
//        applicationContext.beanDefinitionNames.filter {
//            it.contains("database", ignoreCase = true)
//        }.forEach {
//            println("Database related bean: $it")
//        }
//    }
//}