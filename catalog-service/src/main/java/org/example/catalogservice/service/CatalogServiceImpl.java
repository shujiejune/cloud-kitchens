package org.example.catalogservice.service;

import org.example.catalogservice.dto.CatalogItemRequest;
import org.example.catalogservice.dto.BulkImportResponse;
import org.example.catalogservice.dto.BulkImportResponse.RowError;
import org.example.catalogservice.dto.CatalogItemResponse;
import org.example.catalogservice.entity.CatalogItem;
import org.example.catalogservice.exception.ResourceNotFoundException;
import org.example.catalogservice.dao.CatalogItemDAO;
import org.example.catalogservice.mapper.CatalogItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogServiceImpl implements CatalogService {

    private final CatalogItemDAO catalogItemDAO;
    private final CatalogItemMapper catalogItemMapper;

    // ----------------------------------------------------------------
    // findAll
    // ----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<CatalogItemResponse> findAll(Long operatorId) {
        return catalogItemDAO.findAllByOperatorId(operatorId)
                .stream()
                .map(catalogItemMapper::toResponse)
                .toList();
    }

    // ----------------------------------------------------------------
    // findById
    // ----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public CatalogItemResponse findById(Long id, Long operatorId) {
        return catalogItemMapper.toResponse(findOwnedOrThrow(id, operatorId));
    }

    // ----------------------------------------------------------------
    // create
    // ----------------------------------------------------------------

    @Override
    @Transactional
    public CatalogItemResponse create(CatalogItemRequest request, Long operatorId) {
        CatalogItem item = catalogItemMapper.toEntity(request);
        item.setOperatorId(operatorId);
        return catalogItemMapper.toResponse(catalogItemDAO.save(item));
    }

    // ----------------------------------------------------------------
    // update
    // ----------------------------------------------------------------

    @Override
    @Transactional
    public CatalogItemResponse update(Long id, CatalogItemRequest request, Long operatorId) {
        CatalogItem item = findOwnedOrThrow(id, operatorId);
        catalogItemMapper.updateFromRequest(request, item);
        return catalogItemMapper.toResponse(catalogItemDAO.save(item));
    }

    // ----------------------------------------------------------------
    // delete
    // ----------------------------------------------------------------

    @Override
    @Transactional
    public void delete(Long id, Long operatorId) {
        if (!catalogItemDAO.existsByIdAndOperatorId(id, operatorId)) {
            throw new ResourceNotFoundException("Catalog item not found: id=" + id);
        }
        catalogItemDAO.deleteById(id);
    }

    // ----------------------------------------------------------------
    // bulkImport
    // ----------------------------------------------------------------

    @Override
    @Transactional
    public BulkImportResponse bulkImport(MultipartFile file, Long operatorId) {
        List<CatalogItem> toSave  = new ArrayList<>();
        List<RowError>    errors  = new ArrayList<>();
        int rowNumber = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withIgnoreHeaderCase()
                     .withTrim()
                     .parse(reader)) {

            for (CSVRecord record : csvParser) {
                rowNumber++;
                String raw = record.toString();

                try {
                    String name         = record.get("name");
                    String category     = record.get("category");
                    String unit         = record.get("unit");
                    String preferredQtyStr = record.get("preferred_qty");

                    // Basic field validation
                    if (name == null || name.isBlank()) {
                        errors.add(new RowError(rowNumber, raw, "name is required"));
                        continue;
                    }
                    if (unit == null || unit.isBlank()) {
                        errors.add(new RowError(rowNumber, raw, "unit is required"));
                        continue;
                    }

                    BigDecimal preferredQty;
                    try {
                        preferredQty = new BigDecimal(preferredQtyStr);
                        if (preferredQty.compareTo(BigDecimal.ZERO) <= 0) {
                            errors.add(new RowError(rowNumber, raw,
                                    "preferred_qty must be greater than zero"));
                            continue;
                        }
                    } catch (NumberFormatException e) {
                        errors.add(new RowError(rowNumber, raw,
                                "preferred_qty must be a valid decimal number"));
                        continue;
                    }

                    toSave.add(CatalogItem.builder()
                            .operatorId(operatorId)
                            .name(name)
                            .category(category)
                            .unit(unit)
                            .preferredQty(preferredQty)
                            .build());

                } catch (Exception e) {
                    errors.add(new RowError(rowNumber, raw,
                            "Unexpected error parsing row: " + e.getMessage()));
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse CSV for operatorId={}: {}", operatorId, e.getMessage());
            // Return a single error entry describing the parse failure
            return new BulkImportResponse(0, 0, 1,
                    List.of(new RowError(0, "", "Could not read CSV file: " + e.getMessage())));
        }

        if (!toSave.isEmpty()) {
            catalogItemDAO.saveAll(toSave);
        }

        int totalRows    = rowNumber;
        int successCount = toSave.size();
        int failureCount = errors.size();

        log.info("Bulk import operatorId={} total={} success={} failed={}",
                operatorId, totalRows, successCount, failureCount);

        return new BulkImportResponse(totalRows, successCount, failureCount, errors);
    }

    // ----------------------------------------------------------------
    // findByIds  (internal Feign endpoint)
    // ----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<CatalogItemResponse> findByIds(List<Long> ids, Long operatorId) {
        return catalogItemDAO.findByIdInAndOperatorId(ids, operatorId)
                .stream()
                .map(catalogItemMapper::toResponse)
                .toList();
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    private CatalogItem findOwnedOrThrow(Long id, Long operatorId) {
        return catalogItemDAO.findByIdAndOperatorId(id, operatorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Catalog item not found: id=" + id));
    }

}
