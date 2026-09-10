package br.com.buscaproduto.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class SearchPageResponseTest {

    @Test
    void shouldExposeTotalsForCompatibleProductsAndAlternatives() {
        RankedProduct compatible = new RankedProduct(null, true, 100, List.of(), List.of());
        RankedProduct alternative = new RankedProduct(null, false, 40, List.of(), List.of());

        SearchPageResponse response = SearchPageResponse.from(
                List.of(compatible, alternative, alternative),
                0,
                2);

        assertThat(response.content()).containsExactly(compatible, alternative);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalCompatibleElements()).isEqualTo(1);
        assertThat(response.totalAlternativeElements()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.hasNext()).isTrue();
    }
}
