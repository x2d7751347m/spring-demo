package com.example.demo.mappers

import com.example.demo.domain.Beer
import com.example.demo.model.BeerDTO
import org.mapstruct.Mapper
import org.mapstruct.ReportingPolicy

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
interface BeerMapper {
    fun beerDtoToBeer(dto: BeerDTO): Beer

    fun beerToBeerDto(beer: Beer): BeerDTO
}