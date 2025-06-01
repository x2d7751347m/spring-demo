package com.example.demo.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ServiceResult.Ok::class, name = "ok"),
    JsonSubTypes.Type(value = ServiceResult.Err::class, name = "error")
)
sealed class ServiceResult<T> {
    data class Ok<T>(val value: T) : ServiceResult<T>()
    data class Err<T>(val message: String, val errors: List<String> = emptyList()) : ServiceResult<T>()

    inline fun <R> map(transform: (T) -> R): ServiceResult<R> = when (this) {
        is Ok -> Ok(transform(value))
        is Err -> Err(message, errors)
    }

    inline fun onSuccess(action: (T) -> Unit): ServiceResult<T> {
        if (this is Ok) action(value)
        return this
    }

    inline fun onError(action: (String, List<String>) -> Unit): ServiceResult<T> {
        if (this is Err) action(message, errors)
        return this
    }

    fun toResponseEntity(): ResponseEntity<ServiceResult<T>> = when (this) {
        is Ok -> ResponseEntity.ok(this)
        is Err -> ResponseEntity.badRequest().body(this)
    }

    fun toResponseEntity(successStatus: HttpStatus): ResponseEntity<ServiceResult<T>> = when (this) {
        is Ok -> ResponseEntity.status(successStatus).body(this)
        is Err -> ResponseEntity.badRequest().body(this)
    }

    companion object {
        fun <T> ok(value: T): ServiceResult<T> = Ok(value)
        fun <T> error(message: String, errors: List<String> = emptyList()): ServiceResult<T> = Err(message, errors)
    }
}