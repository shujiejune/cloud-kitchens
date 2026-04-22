package org.example.catalogservice.controller;

import org.example.catalogservice.dto.CatalogItemRequest;
import org.example.catalogservice.dto.BulkImportResponse;
import org.example.catalogservice.dto.CatalogItemResponse;
import org.example.catalogservice.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for the operator's ingredient catalog.
 *
 * All endpoints require a valid JWT. The operatorId is extracted from
 * the SecurityContext (@AuthenticationPrincipal) and passed to the
 * service — never read from the request body or query parameters.
 */
@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
@Tag(name = "Catalog", description = "Ingredient catalog management")
public class CatalogController {

    private final CatalogService catalogService;

    // ----------------------------------------------------------------
    // GET /api/v1/catalog/items
    // ----------------------------------------------------------------

    @GetMapping("/items")
    @Operation(summary = "List all catalog items for the authenticated operator")
    public List<CatalogItemResponse> listItems(
            @AuthenticationPrincipal Long operatorId) {
        return catalogService.findAll(operatorId);
    }

    // ----------------------------------------------------------------
    // GET /api/v1/catalog/items/{id}
    // ----------------------------------------------------------------

    @GetMapping("/items/{id}")
    @Operation(summary = "Get a single catalog item by ID")
    public CatalogItemResponse getItem(
            @PathVariable Long id,
            @AuthenticationPrincipal Long operatorId) {
        return catalogService.findById(id, operatorId);
    }

    // ----------------------------------------------------------------
    // POST /api/v1/catalog/items
    // ----------------------------------------------------------------

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new catalog item")
    public CatalogItemResponse createItem(
            @Valid @RequestBody CatalogItemRequest request,
            @AuthenticationPrincipal Long operatorId) {
        return catalogService.create(request, operatorId);
    }

    // ----------------------------------------------------------------
    // PUT /api/v1/catalog/items/{id}
    // ----------------------------------------------------------------

    @PutMapping("/items/{id}")
    @Operation(summary = "Update an existing catalog item")
    public CatalogItemResponse updateItem(
            @PathVariable Long id,
            @Valid @RequestBody CatalogItemRequest request,
            @AuthenticationPrincipal Long operatorId) {
        return catalogService.update(id, request, operatorId);
    }

    // ----------------------------------------------------------------
    // DELETE /api/v1/catalog/items/{id}
    // ----------------------------------------------------------------

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a catalog item")
    public ResponseEntity<Void> deleteItem(
            @PathVariable Long id,
            @AuthenticationPrincipal Long operatorId) {
        catalogService.delete(id, operatorId);
        return ResponseEntity.noContent().build();
    }

    // ----------------------------------------------------------------
    // POST /api/v1/catalog/items/bulk
    // ----------------------------------------------------------------

    /**
     * Accepts a CSV file with columns: name, category, unit, preferred_qty
     * Returns 200 (all ok), 207 (partial), or 400 (all failed).
     */
    @PostMapping(value = "/items/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk import catalog items from a CSV file")
    public ResponseEntity<BulkImportResponse> bulkImport(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Long operatorId) {

        BulkImportResponse result = catalogService.bulkImport(file, operatorId);

        HttpStatus status;
        if (result.failureCount() == 0) {
            status = HttpStatus.OK;
        } else if (result.successCount() == 0) {
            status = HttpStatus.BAD_REQUEST;
        } else {
            status = HttpStatus.MULTI_STATUS;
        }

        return ResponseEntity.status(status).body(result);
    }

    // ----------------------------------------------------------------
    // GET /api/v1/catalog/items/batch  (internal — called by Feign)
    // ----------------------------------------------------------------

    /**
     * Internal endpoint used by procurement-service's CatalogClient Feign.
     * Returns only the items whose IDs are in the query parameter list,
     * still tenant-scoped by operatorId from JWT.
     */
    @GetMapping("/items/batch")
    @Operation(summary = "Batch fetch catalog items by IDs (internal use)")
    public List<CatalogItemResponse> getItemsByIds(
            @RequestParam List<Long> ids,
            @AuthenticationPrincipal Long operatorId) {
        return catalogService.findByIds(ids, operatorId);
    }
}
