package com.example.demo.component

import org.springframework.boot.web.error.ErrorAttributeOptions
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Component
class GlobalErrorAttributes : DefaultErrorAttributes() {

    override fun getErrorAttributes(request: ServerRequest, options: ErrorAttributeOptions): Map<String, Any> {
        val errorAttributes = super.getErrorAttributes(request, options).toMutableMap()
        val error = getError(request)

        when (error) {
            is ResponseStatusException -> {
                errorAttributes["status"] = error.statusCode.value()
                errorAttributes["message"] = error.reason ?: "Internal Server Error"
                errorAttributes["timestamp"] = Instant.now().toString()
                errorAttributes["path"] = request.uri().toString()
            }

            else -> {
                errorAttributes["timestamp"] = Instant.now().toString()
            }
        }

        return errorAttributes
    }
}

data class ErrorMessage(
    val status: String,
    val message: String,
    val timestamp: String,
    val path: String,
)