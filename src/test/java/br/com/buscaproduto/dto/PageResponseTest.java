package br.com.buscaproduto.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class PageResponseTest {

    @Test
    void shouldSliceOrderedItemsAndExposeNavigationMetadata() {
        PageResponse<Integer> response = PageResponse.from(List.of(1, 2, 3, 4, 5), 1, 2);

        assertThat(response.content()).containsExactly(3, 4);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.numberOfElements()).isEqualTo(2);
        assertThat(response.first()).isFalse();
        assertThat(response.last()).isFalse();
        assertThat(response.hasPrevious()).isTrue();
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    void shouldReturnAnEmptyLastPageWhenPageIsPastTheEnd() {
        PageResponse<Integer> response = PageResponse.from(List.of(1, 2, 3), 4, 2);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.numberOfElements()).isZero();
        assertThat(response.last()).isTrue();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.hasPrevious()).isTrue();
    }
}
