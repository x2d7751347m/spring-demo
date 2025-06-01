package com.example.demo.utils

import com.example.demo.model.ServiceResult
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import reactor.core.publisher.Mono

object ServiceResultUtils {

    fun <T> List<ServiceResult<T>>.combine(): ServiceResult<List<T>> {
        val errors = mutableListOf<String>()
        val values = mutableListOf<T>()

        forEach { result ->
            when (result) {
                is ServiceResult.Ok -> values.add(result.value)
                is ServiceResult.Err -> errors.addAll(result.errors)
            }
        }

        return if (errors.isEmpty()) {
            ServiceResult.ok(values)
        } else {
            ServiceResult.error("Multiple validation errors", errors)
        }
    }

    fun <T> T.validateIf(
        condition: Boolean,
        validator: (T) -> List<String>,
    ): ServiceResult<T> {
        return if (condition) {
            val errors = validator(this)
            if (errors.isEmpty()) ServiceResult.ok(this)
            else ServiceResult.error("Validation failed", errors)
        } else {
            ServiceResult.ok(this)
        }
    }
}

fun <T> ServiceResult<T>.toMono(): Mono<ServiceResult<T>> = Mono.just(this)

fun <T> ServiceResult<T>.toMonoResponse(): Mono<ResponseEntity<ServiceResult<T>>> =
    Mono.just(this.toResponseEntity())

fun <T> ServiceResult<T>.toMonoResponse(successStatus: HttpStatus): Mono<ResponseEntity<ServiceResult<T>>> =
    Mono.just(this.toResponseEntity(successStatus))

fun <T> ServiceResult<T>.toMonoStatusOnly(): Mono<ResponseEntity<Void>> =
    when (this) {
        is ServiceResult.Ok -> Mono.just(ResponseEntity.ok().build())
        is ServiceResult.Err -> Mono.just(ResponseEntity.badRequest().build())
    }

fun <T> ServiceResult<T>.toMonoStatusOnly(successStatus: HttpStatus): Mono<ResponseEntity<Void>> =
    when (this) {
        is ServiceResult.Ok -> Mono.just(ResponseEntity.status(successStatus).build())
        is ServiceResult.Err -> Mono.just(ResponseEntity.badRequest().build())
    }