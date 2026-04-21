package org.example.ordersservice.dto;

import java.util.List;

/**
 * Generic paginated wrapper used by list endpoints in orders-service
 * (and reusable across other services for any paginated list).
 *
 * Usage:
 *   PagedResponse<OrderSummaryResponse> page = new PagedResponse<>(
 *       content, page.getNumber(), page.getSize(),
 *       page.getTotalElements(), page.getTotalPages()
 *   );
 *
 * @param <T> the element type (e.g. OrderSummaryResponse)
 */
public record PagedResponse<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean last
) {
    /** Convenience factory from a Spring Data Page object. */
    public static <T> PagedResponse<T> of(org.springframework.data.domain.Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}