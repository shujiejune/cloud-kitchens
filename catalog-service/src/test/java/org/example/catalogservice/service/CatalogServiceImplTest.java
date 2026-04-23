package org.example.catalogservice.service;

import org.example.catalogservice.dao.CatalogItemDAO;
import org.example.catalogservice.dto.BulkImportResponse;
import org.example.catalogservice.dto.CatalogItemRequest;
import org.example.catalogservice.dto.CatalogItemResponse;
import org.example.catalogservice.entity.CatalogItem;
import org.example.catalogservice.exception.ResourceNotFoundException;
import org.example.catalogservice.mapper.CatalogItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServiceImplTest {

    @Mock CatalogItemDAO catalogItemDAO;
    @Mock CatalogItemMapper catalogItemMapper;

    @InjectMocks CatalogServiceImpl catalogService;

    private CatalogItem sampleItem;
    private CatalogItemResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleItem = CatalogItem.builder()
                .id(1L).operatorId(10L).name("Chicken Breast")
                .category("Protein").unit("lb").preferredQty(BigDecimal.TEN)
                .createdAt(LocalDateTime.now()).build();

        sampleResponse = new CatalogItemResponse(1L, 10L, "Chicken Breast", "Protein", "lb",
                BigDecimal.TEN, LocalDateTime.now());
    }

    // ──────────────────── findAll ────────────────────

    @Test
    void findAll_returnsItemsMappedForOperator() {
        when(catalogItemDAO.findAllByOperatorId(10L)).thenReturn(List.of(sampleItem));
        when(catalogItemMapper.toResponse(sampleItem)).thenReturn(sampleResponse);

        List<CatalogItemResponse> result = catalogService.findAll(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Chicken Breast");
    }

    // ──────────────────── findById ────────────────────

    @Test
    void findById_success_returnsResponse() {
        when(catalogItemDAO.findByIdAndOperatorId(1L, 10L)).thenReturn(Optional.of(sampleItem));
        when(catalogItemMapper.toResponse(sampleItem)).thenReturn(sampleResponse);

        CatalogItemResponse result = catalogService.findById(1L, 10L);

        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void findById_notFound_throwsResourceNotFoundException() {
        when(catalogItemDAO.findByIdAndOperatorId(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogService.findById(99L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ──────────────────── create ────────────────────

    @Test
    void create_setsOperatorIdAndPersists() {
        var request = new CatalogItemRequest("Salmon", "Seafood", "lb", BigDecimal.valueOf(5));
        CatalogItem newItem = CatalogItem.builder().id(2L).operatorId(10L)
                .name("Salmon").category("Seafood").unit("lb").preferredQty(BigDecimal.valueOf(5)).build();

        when(catalogItemMapper.toEntity(request)).thenReturn(newItem);
        when(catalogItemDAO.save(newItem)).thenReturn(newItem);
        when(catalogItemMapper.toResponse(newItem)).thenReturn(
                new CatalogItemResponse(2L, 10L, "Salmon", "Seafood", "lb",
                        BigDecimal.valueOf(5), LocalDateTime.now()));

        CatalogItemResponse result = catalogService.create(request, 10L);

        assertThat(result.name()).isEqualTo("Salmon");
        ArgumentCaptor<CatalogItem> captor = ArgumentCaptor.forClass(CatalogItem.class);
        verify(catalogItemDAO).save(captor.capture());
        assertThat(captor.getValue().getOperatorId()).isEqualTo(10L);
    }

    // ──────────────────── update ────────────────────

    @Test
    void update_success_savesUpdatedItem() {
        var request = new CatalogItemRequest("Chicken Thigh", "Protein", "lb", BigDecimal.valueOf(8));
        when(catalogItemDAO.findByIdAndOperatorId(1L, 10L)).thenReturn(Optional.of(sampleItem));
        when(catalogItemDAO.save(sampleItem)).thenReturn(sampleItem);
        when(catalogItemMapper.toResponse(sampleItem)).thenReturn(sampleResponse);

        catalogService.update(1L, request, 10L);

        verify(catalogItemMapper).updateFromRequest(eq(request), eq(sampleItem));
        verify(catalogItemDAO).save(sampleItem);
    }

    @Test
    void update_notFound_throwsResourceNotFoundException() {
        var request = new CatalogItemRequest("X", "Y", "kg", BigDecimal.ONE);
        when(catalogItemDAO.findByIdAndOperatorId(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogService.update(99L, request, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(catalogItemDAO, never()).save(any());
    }

    // ──────────────────── delete ────────────────────

    @Test
    void delete_success_removesItem() {
        when(catalogItemDAO.existsByIdAndOperatorId(1L, 10L)).thenReturn(true);

        catalogService.delete(1L, 10L);

        verify(catalogItemDAO).deleteById(1L);
    }

    @Test
    void delete_notFound_throwsResourceNotFoundException() {
        when(catalogItemDAO.existsByIdAndOperatorId(99L, 10L)).thenReturn(false);

        assertThatThrownBy(() -> catalogService.delete(99L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(catalogItemDAO, never()).deleteById(any());
    }

    // ──────────────────── bulkImport ────────────────────

    @Test
    void bulkImport_validCsv_importsAllRows() {
        String csv = "name,category,unit,preferred_qty\nChicken,Protein,lb,10\nSalmon,Seafood,oz,5\n";
        MockMultipartFile file = new MockMultipartFile("file", "items.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        BulkImportResponse result = catalogService.bulkImport(file, 10L);

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failureCount()).isEqualTo(0);
        verify(catalogItemDAO).saveAll(anyList());
    }

    @Test
    void bulkImport_csvWithErrorRows_reportsPartialSuccess() {
        // Row 2 has blank name, row 3 has invalid qty
        String csv = "name,category,unit,preferred_qty\nChicken,Protein,lb,10\n,,lb,5\nSalmon,Seafood,oz,bad\n";
        MockMultipartFile file = new MockMultipartFile("file", "items.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        BulkImportResponse result = catalogService.bulkImport(file, 10L);

        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(2);
        assertThat(result.errors()).hasSize(2);
    }

    @Test
    void bulkImport_emptyCsv_returnsZeroCounts() {
        String csv = "name,category,unit,preferred_qty\n";
        MockMultipartFile file = new MockMultipartFile("file", "empty.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        BulkImportResponse result = catalogService.bulkImport(file, 10L);

        assertThat(result.totalRows()).isEqualTo(0);
        assertThat(result.successCount()).isEqualTo(0);
        verify(catalogItemDAO, never()).saveAll(anyList());
    }

    @Test
    void bulkImport_zeroQuantity_reportsRowError() {
        String csv = "name,category,unit,preferred_qty\nChicken,Protein,lb,0\n";
        MockMultipartFile file = new MockMultipartFile("file", "items.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        BulkImportResponse result = catalogService.bulkImport(file, 10L);

        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.errors().get(0).reason()).contains("greater than zero");
    }
}
