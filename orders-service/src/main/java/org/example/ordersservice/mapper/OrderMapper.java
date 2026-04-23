package org.example.ordersservice.mapper;

import org.example.ordersservice.dto.OrderResponse;
import org.example.ordersservice.dto.OrderSummaryResponse;
import org.example.ordersservice.entity.OrderLineItemView;
import org.example.ordersservice.entity.OrderView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "lineItemCount", expression = "java(orderView.getLineItems().size())")
    OrderSummaryResponse toSummary(OrderView orderView);

    @Mapping(target = "catalogItemName", source = "catalogItemName")
    @Mapping(target = "vendorName", source = "vendorName")
    OrderResponse.LineItemResponse toLineResponse(
            OrderLineItemView lineItem, String catalogItemName, String vendorName);
}
