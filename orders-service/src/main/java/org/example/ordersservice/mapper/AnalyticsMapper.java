package org.example.ordersservice.mapper;

import org.example.ordersservice.dao.OrderLineItemViewDAO;
import org.example.ordersservice.dto.PriceTrendPoint;
import org.example.ordersservice.dto.SpendSummaryResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AnalyticsMapper {

    SpendSummaryResponse.VendorSpend toVendorSpend(OrderLineItemViewDAO.VendorSpendProjection p);

    PriceTrendPoint toTrendPoint(OrderLineItemViewDAO.PriceTrendProjection p);
}
