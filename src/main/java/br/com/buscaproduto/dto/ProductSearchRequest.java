package br.com.buscaproduto.dto;

import jakarta.validation.constraints.Size;

/**
 * Search over catalog products (last level of the hierarchy). Every field is optional:
 * segment, family and material work as cascading filters, brand narrows inside them and
 * the free text must match every term (accent and case insensitive).
 */
public record ProductSearchRequest(
        @Size(max = 200) String query,
        @Size(max = 20) String segmentCode,
        @Size(max = 20) String familyCode,
        @Size(max = 30) String materialCode,
        @Size(max = 120) String brand,
        Boolean onlyFavorites) {
}
