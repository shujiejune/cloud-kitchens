package org.example.authservice.mapper;

import org.example.authservice.dto.OperatorResponse;
import org.example.authservice.entity.Operator;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuthMapper {

    @Mapping(target = "status", expression = "java(op.getStatus().name())")
    OperatorResponse toResponse(Operator op);
}
