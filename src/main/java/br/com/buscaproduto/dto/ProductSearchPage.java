package br.com.buscaproduto.dto;

import java.util.List;
import java.util.Map;

import br.com.buscaproduto.model.CatalogProduct;

/**
 * One page of catalog products plus the facets used by the filters:
 * {@code brands} ignores the brand filter and {@code materialCounts} ignores the material filter,
 * so the user can always see the other choices available in the current scope.
 */
public record ProductSearchPage(
        List<Result> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<Facet> brands,
        Map<String, Long> materialCounts) {

    public record Result(
            CatalogProduct product,
            String segmentName,
            String familyName,
            String materialName,
            String materialImageUrl) {
    }

    public record Facet(String name, long count) {
    }
}
