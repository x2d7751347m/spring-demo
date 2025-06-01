package com.example.demo.controllers

import com.example.demo.model.*
import com.example.demo.services.BeerService
import com.example.demo.utils.toMonoResponse
import com.example.demo.utils.toMonoStatusOnly
import com.example.demo.validator.beerCreateListValidator
import com.example.demo.validator.beerSearchRequestValidator
import com.example.demo.validator.beerUpdateListValidator
import com.example.demo.validator.commons.idListValidator
import io.konform.validation.ValidationError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactor.mono
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

@RestController
class BeerController(
    private val beerService: BeerService,
) {
    companion object {
        const val BEER_PATH: String = "/api/v2/beer"
        const val BEER_PATH_ID: String = "$BEER_PATH/{beerId}"
    }

    @DeleteMapping(BEER_PATH)
    fun deleteBeersByIds(@RequestBody ids: List<Long>): Mono<ResponseEntity<Void>> {
        return mono {
            validateAndExecute(
                data = ids,
                validator = { idListValidator(it).errors },
                action = {
                    beerService.deleteEntitiesById(it)
                }
            )
        }.flatMap { result ->
            result.toMonoStatusOnly(HttpStatus.NO_CONTENT)
        }
    }

    @PatchMapping(BEER_PATH)
    fun patchBeers(
        @RequestBody updateDTOs: List<BeerUpdateDTO>,
    ): Mono<ResponseEntity<Void>> {
        return mono {
            validateAndExecute(
                data = updateDTOs,
                validator = { beerUpdateListValidator(it).errors },
                action = {
                    beerService.patchEntities(it)
                }
            )
        }.flatMap { result ->
            result.toMonoStatusOnly(HttpStatus.OK)
        }
    }

    @PostMapping(BEER_PATH)
    fun createNewBeers(@RequestBody createDTOs: List<BeerCreateDTO>): Mono<ResponseEntity<ServiceResult<List<BeerDTO>>>> {
        return mono {
            validateAndExecute(
                data = createDTOs,
                validator = { beerCreateListValidator(it).errors },
                action = {
                    beerService.saveNewEntities(it)
                }
            )
        }.flatMap { result ->
            result.toMonoResponse(HttpStatus.CREATED)
        }
    }

    @PostMapping("$BEER_PATH/get")
    suspend fun getBeers(@RequestBody searchRequestBody: BeerSearchRequest): Flow<BeerDTO> {
        beerSearchRequestValidator(searchRequestBody).errors.let { errors ->
            if (errors.isNotEmpty()) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Validation failed: ${errors.joinToString(", ")}"
                )
            }
        }

        return beerService.getEntities(searchRequestBody)
    }

    private suspend fun <T, R> validateAndExecute(
        data: T,
        validator: (T) -> List<ValidationError>,
        action: suspend (T) -> R,
    ): ServiceResult<R> {
        val errors = validator(data)
        return if (errors.isNotEmpty()) {
            ServiceResult.error("Validation failed", errors.map { error -> error.message })
        } else {
            ServiceResult.ok(action(data))
        }
    }
}