package br.com.buscaproduto.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        int numberOfElements,
        boolean first,
        boolean last,
        boolean hasNext,
        boolean hasPrevious) {

    public static <T> PageResponse<T> from(List<T> orderedItems, int page, int size) {
        if (page < 0) throw new IllegalArgumentException("A página não pode ser negativa.");
        if (size < 1) throw new IllegalArgumentException("O tamanho da página deve ser maior que zero.");

        long totalElements = orderedItems.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil(totalElements / (double) size);
        int fromIndex = (int) Math.min((long) page * size, totalElements);
        int toIndex = (int) Math.min((long) fromIndex + size, totalElements);
        List<T> content = List.copyOf(orderedItems.subList(fromIndex, toIndex));

        return new PageResponse<>(
                content,
                page,
                size,
                totalElements,
                totalPages,
                content.size(),
                page == 0,
                totalPages == 0 || page >= totalPages - 1,
                page + 1 < totalPages,
                page > 0);
    }
}
