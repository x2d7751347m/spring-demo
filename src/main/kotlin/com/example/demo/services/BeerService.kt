package com.example.demo.services

import com.example.demo.model.BeerCreateDTO
import com.example.demo.model.BeerDTO
import com.example.demo.model.BeerSearchRequest
import com.example.demo.model.BeerUpdateDTO
import kotlinx.coroutines.flow.Flow

interface BeerService {
    suspend fun getEntities(
        searchRequest: BeerSearchRequest,
    ): Flow<BeerDTO>

    suspend fun saveNewEntities(beerCreateDTOs: List<BeerCreateDTO>): List<BeerDTO>

    suspend fun patchEntities(beerUpdateDTOs: List<BeerUpdateDTO>)

    suspend fun deleteEntitiesById(ids: List<Long>)
}
