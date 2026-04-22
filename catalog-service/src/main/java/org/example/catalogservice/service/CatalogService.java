package org.example.catalogservice.service;

import org.example.catalogservice.dto.CatalogItemRequest;
import org.example.catalogservice.dto.BulkImportResponse;
import org.example.catalogservice.dto.CatalogItemResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Business operations for the operator's ingredient catalog.
 *
 * All methods accept operatorId as an explicit parameter — it is never
 * inferred from the request body.  The controller extracts it from
 * the SecurityContext (populated by the JWT filter) and passes it here.
 */
public interface CatalogService {

    /** Returns all catalog items for the given operator. */
    List<CatalogItemResponse> findAll(Long operatorId);

    /**
     * Returns a single catalog item, asserting ownership.
     *
     * @throws org.example.catalogservice.exception.ResourceNotFoundException if not found
     *         or if the item belongs to a different operator.
     */
    CatalogItemResponse findById(Long id, Long operatorId);

    /**
     * Creates a new catalog item for the operator.
     * operatorId is stamped onto the entity — not taken from the request body.
     */
    CatalogItemResponse create(CatalogItemRequest request, Long operatorId);

    /**
     * Replaces all mutable fields of an existing catalog item.
     *
     * @throws org.example.catalogservice.exception.ResourceNotFoundException if not found.
     */
    CatalogItemResponse update(Long id, CatalogItemRequest request, Long operatorId);

    /**
     * Deletes a catalog item, asserting ownership.
     *
     * @throws org.example.catalogservice.exception.ResourceNotFoundException if not found.
     */
    void delete(Long id, Long operatorId);

    /**
     * Parses a CSV file and creates one CatalogItem per valid row.
     *
     * Expected CSV columns (header row required):
     *   name, category, unit, preferred_qty
     *
     * Invalid rows are collected into BulkImportResponse.errors rather
     * than aborting the entire import.  Valid rows are batch-saved.
     *
     * HTTP status hint: 200 if all rows ok, 207 if mixed, 400 if all failed.
     */
    BulkImportResponse bulkImport(MultipartFile file, Long operatorId);

    /**
     * Internal method exposed via Feign to Procurement Service.
     * Returns only the items whose IDs are in the provided list,
     * always scoped to the operator.
     */
    List<CatalogItemResponse> findByIds(List<Long> ids, Long operatorId);
}
