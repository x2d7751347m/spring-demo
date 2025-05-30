@file:Suppress("PropertyName")

plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.spring") version "2.1.21"
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.asciidoctor.jvm.convert") version "3.3.2"
    kotlin("kapt") version "2.1.21"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

val snippetsDir by extra { file("build/generated-snippets") }

val mapstruct_version: String by project
val bucket4j_version: String by project
val kotlin_logging_version: String by project
val kotlinx_datetime_version: String by project
val konform_version: String by project
val awaitility_version: String by project
val guava_version: String by project

dependencies {
    implementation("org.mapstruct:mapstruct:$mapstruct_version")
    kapt("org.mapstruct:mapstruct-processor:$mapstruct_version")
    kaptTest("org.mapstruct:mapstruct-processor:$mapstruct_version")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("io.r2dbc:r2dbc-h2")
    implementation("com.bucket4j:bucket4j_jdk17-core:$bucket4j_version") // Rate Limit
    runtimeOnly("io.github.oshai:kotlin-logging-jvm:$kotlin_logging_version")
    implementation(group = "org.apache.commons", name = "commons-lang3")
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinx_datetime_version")
    implementation("io.konform:konform-jvm:$konform_version")
    testImplementation("org.awaitility:awaitility-kotlin:$awaitility_version")
    implementation("com.google.guava:guava:$guava_version")
//	implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
//	implementation("io.r2dbc:r2dbc-pool:1.0.2.RELEASE")
//	implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-quartz")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
//	runtimeOnly("org.postgresql:postgresql")
//	runtimeOnly("org.postgresql:r2dbc-postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("org.springframework.restdocs:spring-restdocs-webtestclient")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
//	testImplementation("org.testcontainers:postgresql")
//	testImplementation("org.testcontainers:r2dbc")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

kapt {
    correctErrorTypes = true
    showProcessorStats = true
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.test {
    // Enable detailed test output for debugging
    // Disable in production to reduce log noise
//	testLogging {
//		outputs.upToDateWhen { false }
//		showStandardStreams = true
//		events("passed", "skipped", "failed")
//		exceptionFormat = TestExceptionFormat.FULL
//	}

    outputs.dir(snippetsDir)
    jvmArgs("-Xshare:off")
}

tasks.asciidoctor {
    inputs.dir(snippetsDir)
    dependsOn(tasks.test)
}