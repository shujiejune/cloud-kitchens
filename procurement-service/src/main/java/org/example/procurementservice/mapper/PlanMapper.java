package org.example.procurementservice.mapper;

import org.example.procurementservice.document.Plan;
import org.example.procurementservice.dto.PurchasePlanResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PlanMapper {

    @Mapping(target = "planId", source = "id")
    @Mapping(target = "items", source = "items", defaultExpression = "java(java.util.List.of())")
    @Mapping(target = "vendorSubtotals", source = "vendorSubtotals", defaultExpression = "java(java.util.List.of())")
    @Mapping(target = "vendorWarnings", source = "vendorWarnings", defaultExpression = "java(java.util.List.of())")
    PurchasePlanResponse toResponse(Plan plan);

    PurchasePlanResponse.PlanLineItem toPlanLineItem(Plan.PlanItem item);

    PurchasePlanResponse.VendorSubtotal toVendorSubtotal(Plan.VendorSubtotalSnapshot s);
}
