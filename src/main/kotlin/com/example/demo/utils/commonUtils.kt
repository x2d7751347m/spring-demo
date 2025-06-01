package com.example.demo.utils

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.Instant

fun createErrorResponse(
    message: String,
    status: HttpStatus = HttpStatus.BAD_REQUEST,
): ResponseEntity<Map<String, Any>> {
    val errorBody = mapOf(
        "timestamp" to Instant.now().toString(),
        "status" to status.value(),
        "error" to status.reasonPhrase,
        "message" to message
    )
    return ResponseEntity.status(status).body(errorBody)
}