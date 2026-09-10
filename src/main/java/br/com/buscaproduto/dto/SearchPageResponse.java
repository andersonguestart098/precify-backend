package br.com.buscaproduto.dto;

import java.util.List;

public record SearchPageResponse(
        List<RankedProduct> content,
        int page,
        int size,
        long totalElements,
        long totalCompatibleElements,
        long totalAlternativeElements,
        int totalPages,
        int numberOfElements,
        boolean first,
        boolean last,
        boolean hasNext,
        boolean hasPrevious) {

    public static SearchPageResponse from(List<RankedProduct> orderedResults, int page, int size) {
        PageResponse<RankedProduct> pagination = PageResponse.from(orderedResults, page, size);
        long totalCompatibleElements = orderedResults.stream()
                .filter(RankedProduct::compatible)
                .count();

        return new SearchPageResponse(
                pagination.content(),
                pagination.page(),
                pagination.size(),
                pagination.totalElements(),
                totalCompatibleElements,
                pagination.totalElements() - totalCompatibleElements,
                pagination.totalPages(),
                pagination.numberOfElements(),
                pagination.first(),
                pagination.last(),
                pagination.hasNext(),
                pagination.hasPrevious());
    }
}
