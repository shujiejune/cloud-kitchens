package org.example.ordersservice.service;

import feign.FeignException;
import org.example.catalogservice.dto.CatalogItemResponse;
import org.example.ordersservice.client.CatalogClient;
import org.example.ordersservice.client.ProcurementRetryClient;
import org.example.ordersservice.dao.OrderLineItemViewDAO;
import org.example.ordersservice.dao.OrderViewDAO;
import org.example.ordersservice.dao.VendorViewDAO;
import org.example.ordersservice.dto.LineItemStatusResponse;
import org.example.ordersservice.dto.OrderHistoryFilter;
import org.example.ordersservice.dto.OrderSummaryResponse;
import org.example.ordersservice.dto.PagedResponse;
import org.example.ordersservice.entity.OrderLineItemView;
import org.example.ordersservice.entity.OrderView;
import org.example.ordersservice.entity.VendorView;
import org.example.ordersservice.exception.CatalogLookupException;
import org.example.ordersservice.exception.LineItemNotFoundException;
import org.example.ordersservice.exception.OrderNotFoundException;
import org.example.ordersservice.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrdersServiceImplTest {

    @Mock OrderViewDAO orderViewDAO;
    @Mock OrderLineItemViewDAO orderLineItemViewDAO;
    @Mock VendorViewDAO vendorViewDAO;
    @Mock CatalogClient catalogClient;
    @Mock ProcurementRetryClient procurementRetryClient;
    @Mock OrderMapper orderMapper;

    @InjectMocks OrdersServiceImpl ordersService;

    private OrderView sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrder = mock(OrderView.class);
        when(sampleOrder.getId()).thenReturn(10L);
        when(sampleOrder.getOperatorId()).thenReturn(1L);
        when(sampleOrder.getStatus()).thenReturn("SUBMITTED");
        when(sampleOrder.getTotalCost()).thenReturn(BigDecimal.valueOf(200));
        when(sampleOrder.getEstimatedSavings()).thenReturn(BigDecimal.valueOf(20));
        when(sampleOrder.getSubmittedAt()).thenReturn(LocalDateTime.now());
        when(sampleOrder.getCreatedAt()).thenReturn(LocalDateTime.now());
    }

    // ──────────────────── listOrders ────────────────────

    @Test
    void listOrders_noFilters_delegatesToFindAll() {
        OrderHistoryFilter emptyFilter = new OrderHistoryFilter(null, null, null, null);
        Pageable pageable = PageRequest.of(0, 10);
        when(orderViewDAO.findAllByOperatorId(eq(1L), any())).thenReturn(new PageImpl<>(List.of(sampleOrder)));
        when(orderMapper.toSummary(sampleOrder)).thenReturn(mock(OrderSummaryResponse.class));

        PagedResponse<OrderSummaryResponse> result = ordersService.listOrders(1L, emptyFilter, pageable);

        assertThat(result.content()).hasSize(1);
        verify(orderViewDAO).findAllByOperatorId(eq(1L), any());
    }

    @Test
    void listOrders_withVendorFilter_delegatesToSearch() {
        OrderHistoryFilter filter = new OrderHistoryFilter(null, null, 2L, null);
        Pageable pageable = PageRequest.of(0, 10);
        when(orderViewDAO.search(eq(1L), any(), any(), eq(2L), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        ordersService.listOrders(1L, filter, pageable);

        verify(orderViewDAO).search(eq(1L), any(), any(), eq(2L), any(), any());
    }

    // ──────────────────── getOrder ────────────────────

    @Test
    void getOrder_success_hydratesItemAndVendorNames() {
        OrderLineItemView lineItem = mock(OrderLineItemView.class);
        when(lineItem.getCatalogItemId()).thenReturn(5L);
        when(lineItem.getVendorId()).thenReturn(1L);

        VendorView vendor = mock(VendorView.class);
        when(vendor.getId()).thenReturn(1L);
        when(vendor.getName()).thenReturn("Amazon");

        when(orderViewDAO.findByIdAndOperatorId(10L, 1L)).thenReturn(Optional.of(sampleOrder));
        when(orderLineItemViewDAO.findAllByOrderId(10L)).thenReturn(List.of(lineItem));
        when(catalogClient.getItemsByIds(anyList(), anyString()))
                .thenReturn(List.of(new CatalogItemResponse(5L, 1L, "Chicken", "Protein", "lb",
                        BigDecimal.TEN, LocalDateTime.now())));
        when(vendorViewDAO.findAllById(anyList())).thenReturn(List.of(vendor));

        ordersService.getOrder(1L, 10L, "Bearer token");

        verify(orderMapper).toLineResponse(eq(lineItem), eq("Chicken"), eq("Amazon"));
    }

    @Test
    void getOrder_notFound_throwsOrderNotFoundException() {
        when(orderViewDAO.findByIdAndOperatorId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ordersService.getOrder(1L, 99L, "Bearer token"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void getOrder_catalogLookupFails_throwsCatalogLookupException() {
        OrderLineItemView lineItem = mock(OrderLineItemView.class);
        when(lineItem.getCatalogItemId()).thenReturn(5L);
        when(lineItem.getVendorId()).thenReturn(1L);

        when(orderViewDAO.findByIdAndOperatorId(10L, 1L)).thenReturn(Optional.of(sampleOrder));
        when(orderLineItemViewDAO.findAllByOrderId(10L)).thenReturn(List.of(lineItem));
        when(catalogClient.getItemsByIds(anyList(), anyString()))
                .thenThrow(mock(FeignException.class));

        assertThatThrownBy(() -> ordersService.getOrder(1L, 10L, "Bearer token"))
                .isInstanceOf(CatalogLookupException.class);
    }

    // ──────────────────── retrySubOrder ────────────────────

    @Test
    void retrySubOrder_orderNotFound_throwsOrderNotFoundException() {
        when(orderViewDAO.findByIdAndOperatorId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ordersService.retrySubOrder(1L, 99L, 5L, "Bearer token"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void retrySubOrder_lineItemNotFound_throwsLineItemNotFoundException() {
        when(orderViewDAO.findByIdAndOperatorId(10L, 1L)).thenReturn(Optional.of(sampleOrder));
        when(orderLineItemViewDAO.findAllByOrderId(10L)).thenReturn(List.of());

        assertThatThrownBy(() -> ordersService.retrySubOrder(1L, 10L, 999L, "Bearer token"))
                .isInstanceOf(LineItemNotFoundException.class);
    }

    @Test
    void retrySubOrder_success_returnsUpdatedLineItemResponse() {
        OrderLineItemView lineItem = mock(OrderLineItemView.class);
        when(lineItem.getId()).thenReturn(5L);
        when(lineItem.getCatalogItemId()).thenReturn(3L);
        when(lineItem.getVendorId()).thenReturn(1L);
        when(lineItem.getQuantity()).thenReturn(BigDecimal.TEN);
        when(lineItem.getUnitPrice()).thenReturn(BigDecimal.valueOf(3.5));

        var retryResult = new LineItemStatusResponse(5L, 3L, 1L, "Amazon",
                BigDecimal.TEN, BigDecimal.valueOf(35), "CONFIRMED", "ref-123", null);

        when(orderViewDAO.findByIdAndOperatorId(10L, 1L)).thenReturn(Optional.of(sampleOrder));
        when(orderLineItemViewDAO.findAllByOrderId(10L)).thenReturn(List.of(lineItem));
        when(procurementRetryClient.retrySubOrder(10L, 5L, "Bearer token")).thenReturn(retryResult);
        when(catalogClient.getItemsByIds(anyList(), anyString())).thenReturn(List.of());
        when(vendorViewDAO.findAllById(anyList())).thenReturn(List.of());

        var result = ordersService.retrySubOrder(1L, 10L, 5L, "Bearer token");

        assertThat(result.subOrderStatus()).isEqualTo("CONFIRMED");
    }
}
