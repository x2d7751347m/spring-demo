package com.example.demo.controllers

import com.example.demo.model.BeerCreateDTO
import com.example.demo.model.BeerDTO
import com.example.demo.model.BeerSearchRequest
import com.example.demo.model.BeerUpdateDTO
import com.example.demo.services.BeerService
import com.example.demo.validator.beerCreateListValidator
import com.example.demo.validator.beerSearchRequestValidator
import com.example.demo.validator.beerUpdateListValidator
import com.example.demo.validator.commons.idListValidator
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
            idListValidator(ids).errors.let { errors ->
                if (errors.isNotEmpty()) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Validation failed: ${errors.joinToString(", ")}"
                    )
                }
            }
            beerService.deleteEntitiesById(ids)
            ResponseEntity.noContent().build()
        }
    }

    @PatchMapping(BEER_PATH)
    fun patchBeers(
        @RequestBody updateDTOs: List<BeerUpdateDTO>,
    ): Mono<ResponseEntity<Void>> {
        return mono {
            beerUpdateListValidator(updateDTOs).errors.let { errors ->
                if (errors.isNotEmpty()) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Validation failed: ${errors.joinToString(", ")}"
                    )
                }
            }

            beerService.patchEntities(updateDTOs)
            ResponseEntity.ok().build()
        }
    }

    @PostMapping(BEER_PATH)
    fun createNewBeers(@RequestBody createDTOs: List<BeerCreateDTO>): Mono<ResponseEntity<List<BeerDTO>>> {
        return mono {
            beerCreateListValidator(createDTOs).errors.let { errors ->
                if (errors.isNotEmpty()) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Validation failed: ${errors.joinToString(", ")}"
                    )
                }
            }

            val savedBeers = beerService.saveNewEntities(createDTOs)
            ResponseEntity.status(HttpStatus.CREATED).body(savedBeers)
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
}