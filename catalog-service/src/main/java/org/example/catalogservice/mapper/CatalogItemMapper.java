package org.example.catalogservice.mapper;

import org.example.catalogservice.dto.CatalogItemRequest;
import org.example.catalogservice.dto.CatalogItemResponse;
import org.example.catalogservice.entity.CatalogItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CatalogItemMapper {

    CatalogItemResponse toResponse(CatalogItem item);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "operatorId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    CatalogItem toEntity(CatalogItemRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "operatorId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateFromRequest(CatalogItemRequest request, @MappingTarget CatalogItem item);
}
