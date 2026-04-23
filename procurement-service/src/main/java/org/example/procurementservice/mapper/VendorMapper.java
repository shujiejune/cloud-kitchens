package org.example.procurementservice.mapper;

import org.example.procurementservice.dto.VendorResponse;
import org.example.procurementservice.entity.Vendor;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface VendorMapper {

    VendorResponse toResponse(Vendor vendor);
}
