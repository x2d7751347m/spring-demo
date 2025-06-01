package com.example.demo.services

import com.example.demo.mappers.BeerMapper
import com.example.demo.model.BeerCreateDTO
import com.example.demo.model.BeerDTO
import com.example.demo.model.BeerSearchRequest
import com.example.demo.model.BeerUpdateDTO
import com.example.demo.repositories.BeerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.springframework.stereotype.Service

/**
 * Created by jt, Spring Framework Guru.
 */
@Service
class BeerServiceImpl(
    private val beerRepository: BeerRepository,
    private val beerMapper: BeerMapper,
) : BeerService {

    override suspend fun getEntities(
        searchRequest: BeerSearchRequest,
    ): Flow<BeerDTO> =
        beerRepository.getEntities(
            page = searchRequest.page ?: 1,
            size = searchRequest.size ?: 1,
            ids = searchRequest.ids,
            beerName = searchRequest.beerName,
            beerNameContains = searchRequest.beerNameContains,
            beerStyle = searchRequest.beerStyle,
            beerStyleContains = searchRequest.beerStyleContains,
            upc = searchRequest.upc,
            quantityOnHand = searchRequest.quantityOnHand,
            minPrice = searchRequest.minPrice,
            maxPrice = searchRequest.maxPrice
        ).map(beerMapper::beerToBeerDto)


    override suspend fun saveNewEntities(beerCreateDTOs: List<BeerCreateDTO>): List<BeerDTO> =
        beerRepository.insertEntities(beerCreateDTOs.map(beerMapper::beerCreateDtoToBeer))
            .map(beerMapper::beerToBeerDto)


    override suspend fun patchEntities(beerUpdateDTOs: List<BeerUpdateDTO>) {
        return beerRepository.patchEntities(beerUpdateDTOs)
    }

    override suspend fun deleteEntitiesById(ids: List<Long>) {
        beerRepository.deleteEntities(ids)
    }
}
