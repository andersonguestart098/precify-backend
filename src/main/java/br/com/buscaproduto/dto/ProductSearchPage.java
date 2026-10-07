package br.com.buscaproduto.dto;

import java.util.List;
import java.util.Map;

import br.com.buscaproduto.model.CatalogProduct;

/**
 * One page of the product listing plus the facets used by the filters.
 *
 * Each result is either a {@code PRODUCT} (last level) or a {@code MATERIAL} that has no product yet,
 * carried with the same card data the material view uses ({@code material}: offers, image, logo).
 * {@code brands} ignores the brand filter and {@code materialCounts} ignores the material filter, so
 * the user always sees the other choices available in the current scope.
 */
public record ProductSearchPage(
        List<Result> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        long productElements,
        long materialElements,
        List<Facet> brands,
        Map<String, Long> materialCounts) {

    public static final String PRODUCT = "PRODUCT";
    public static final String MATERIAL = "MATERIAL";

    public record Result(
            String type,
            CatalogProduct product,
            String segmentName,
            String familyName,
            String materialName,
            String materialImageUrl,
            CatalogSearchPage.Result material) {
    }

    public record Facet(String name, long count) {
    }
}
