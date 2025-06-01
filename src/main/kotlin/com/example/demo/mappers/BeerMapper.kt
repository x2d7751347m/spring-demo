package com.example.demo.mappers

import com.example.demo.domain.Beer
import com.example.demo.model.BeerCreateDTO
import com.example.demo.model.BeerDTO
import org.mapstruct.Mapper
import org.mapstruct.ReportingPolicy

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
interface BeerMapper {
    fun beerCreateDtoToBeer(beerCreateDTO: BeerCreateDTO): Beer
    fun beerToBeerDto(beer: Beer): BeerDTO
}