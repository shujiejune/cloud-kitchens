package org.example.ordersservice.service;

import org.example.ordersservice.dao.OrderLineItemViewDAO;
import org.example.ordersservice.dto.SpendSummaryResponse;
import org.example.ordersservice.exception.InvalidAnalyticsRangeException;
import org.example.ordersservice.mapper.AnalyticsMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceImplTest {

    @Mock OrderLineItemViewDAO orderLineItemViewDAO;
    @Mock AnalyticsMapper analyticsMapper;

    @InjectMocks AnalyticsServiceImpl analyticsService;

    @Test
    void getSpendSummary_validDays_returnsAggregatedResponse() {
        var totals = mock(OrderLineItemViewDAO.SpendTotalsProjection.class);
        when(totals.getTotalSpend()).thenReturn(BigDecimal.valueOf(500));
        when(totals.getTotalSavings()).thenReturn(BigDecimal.valueOf(50));
        when(totals.getOrderCount()).thenReturn(3L);
        when(orderLineItemViewDAO.aggregateTotals(eq(1L), any())).thenReturn(totals);
        when(orderLineItemViewDAO.aggregateSpendByVendor(eq(1L), any())).thenReturn(List.of());

        SpendSummaryResponse result = analyticsService.getSpendSummary(1L, 30);

        assertThat(result.totalSpend()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(result.totalSavings()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(result.orderCount()).isEqualTo(3L);
        assertThat(result.periodLabel()).isEqualTo("Last 30 days");
    }

    @Test
    void getSpendSummary_singularDay_returnsCorrectLabel() {
        var totals = mock(OrderLineItemViewDAO.SpendTotalsProjection.class);
        when(totals.getTotalSpend()).thenReturn(BigDecimal.ZERO);
        when(totals.getTotalSavings()).thenReturn(BigDecimal.ZERO);
        when(totals.getOrderCount()).thenReturn(0L);
        when(orderLineItemViewDAO.aggregateTotals(eq(2L), any())).thenReturn(totals);
        when(orderLineItemViewDAO.aggregateSpendByVendor(eq(2L), any())).thenReturn(List.of());

        SpendSummaryResponse result = analyticsService.getSpendSummary(2L, 1);

        assertThat(result.periodLabel()).isEqualTo("Last 1 day");
    }

    @Test
    void getSpendSummary_zeroDays_throwsInvalidAnalyticsRangeException() {
        assertThatThrownBy(() -> analyticsService.getSpendSummary(1L, 0))
                .isInstanceOf(InvalidAnalyticsRangeException.class)
                .hasMessageContaining("days must be between 1 and 365");
    }

    @Test
    void getSpendSummary_tooManyDays_throwsInvalidAnalyticsRangeException() {
        assertThatThrownBy(() -> analyticsService.getSpendSummary(1L, 366))
                .isInstanceOf(InvalidAnalyticsRangeException.class);
    }

    @Test
    void getSpendByVendor_validDays_delegatesToDAO() {
        when(orderLineItemViewDAO.aggregateSpendByVendor(eq(1L), any())).thenReturn(List.of());

        analyticsService.getSpendByVendor(1L, 7);

        verify(orderLineItemViewDAO).aggregateSpendByVendor(eq(1L), any());
    }

    @Test
    void getPriceTrends_validDays_delegatesToDAO() {
        when(orderLineItemViewDAO.findPriceTrend(eq(1L), eq(42L), any())).thenReturn(List.of());

        analyticsService.getPriceTrends(1L, 42L, 90);

        verify(orderLineItemViewDAO).findPriceTrend(eq(1L), eq(42L), any());
    }
}
