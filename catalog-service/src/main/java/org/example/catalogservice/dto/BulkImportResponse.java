package org.example.catalogservice.dto;

import java.util.List;

/**
 * Response for POST /api/v1/catalog/items/bulk (CSV multipart upload).
 *
 * Reports a per-row outcome so the operator knows exactly which rows
 * succeeded and which failed validation without re-uploading the whole file.
 *
 * HTTP status is 207 Multi-Status when there is a mix of successes
 * and failures, 200 when all rows succeeded, 400 when all rows failed.
 */
public record BulkImportResponse(
        int totalRows,
        int successCount,
        int failureCount,
        List<RowError> errors
) {

    /**
     * Describes a single failed row.
     *
     * @param rowNumber  1-based line number in the uploaded CSV.
     * @param rawContent The original CSV line that failed (for user reference).
     * @param reason     Human-readable validation error message.
     */
    public record RowError(int rowNumber, String rawContent, String reason) {}
}
