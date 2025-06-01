package com.example.demo.component

import org.springframework.boot.autoconfigure.web.WebProperties
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler
import org.springframework.boot.web.error.ErrorAttributeOptions
import org.springframework.boot.web.reactive.error.ErrorAttributes
import org.springframework.context.ApplicationContext
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.*
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.time.Instant

@Component
@Order(-2)
class GlobalErrorWebExceptionHandler(
    errorAttributes: ErrorAttributes,
    webProperties: WebProperties,
    applicationContext: ApplicationContext,
) : AbstractErrorWebExceptionHandler(errorAttributes, webProperties.resources, applicationContext) {

    override fun getRoutingFunction(errorAttributes: ErrorAttributes): RouterFunction<ServerResponse> {
        return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse)
    }

    private fun renderErrorResponse(request: ServerRequest): Mono<ServerResponse> {
        val error = getError(request)

        return when (error) {
            is ResponseStatusException -> {
                val errorBody = mapOf(
                    "timestamp" to Instant.now(),
                    "status" to error.statusCode.value(),
                    "message" to error.reason,
                    "path" to request.uri().toString()
                )

                ServerResponse.status(error.statusCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(errorBody)
            }

            else -> {
                val errorPropertiesMap = getErrorAttributes(
                    request,
                    ErrorAttributeOptions.defaults()
                )

                ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(errorPropertiesMap)
            }
        }
    }
}