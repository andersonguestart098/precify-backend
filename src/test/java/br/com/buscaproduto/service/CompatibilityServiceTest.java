package br.com.buscaproduto.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.buscaproduto.dto.RankedProduct;
import br.com.buscaproduto.dto.SearchRequest;
import br.com.buscaproduto.dto.TechnicalCriterion;
import br.com.buscaproduto.enums.CriterionMode;
import br.com.buscaproduto.enums.CriterionOperator;
import br.com.buscaproduto.model.Product;

class CompatibilityServiceTest {
    private final CompatibilityService service = new CompatibilityService();

    @Test
    void shouldKeepProductsAtOrAboveMinimumAndOrderByPreferences() {
        Product exact = product("1", "5000 K", "40 W", "IP65");
        Product partial = product("2", "4000 K", "42 W", "IP54");
        Product incompatible = product("3", "3000 K", "40 W", "IP65");

        SearchRequest request = new SearchRequest("Iluminação", "luminária", List.of(
                criterion("temperature", "4000 K", CriterionMode.REQUIRED, CriterionOperator.MINIMUM, 40),
                criterion("power", "40 W", CriterionMode.PREFERRED, CriterionOperator.EXACT, 30),
                criterion("protection", "IP65", CriterionMode.PREFERRED, CriterionOperator.EXACT, 30)
        ), true);

        List<RankedProduct> result = service.rank(List.of(partial, incompatible, exact), request);

        assertThat(result).extracting(item -> item.product().id()).containsExactly("1", "2", "3");
        assertThat(result.getFirst().compatible()).isTrue();
        assertThat(result.getFirst().compatibility()).isEqualTo(100);
        assertThat(result.getLast().compatible()).isFalse();
    }

    @Test
    void shouldHideAlternativesWhenDisabled() {
        Product incompatible = product("3", "3000 K", "40 W", "IP65");
        SearchRequest request = new SearchRequest("Iluminação", "", List.of(
                criterion("temperature", "4000 K", CriterionMode.REQUIRED, CriterionOperator.MINIMUM, 100)
        ), false);

        assertThat(service.rank(List.of(incompatible), request)).isEmpty();
    }

    private TechnicalCriterion criterion(String key, String value, CriterionMode mode, CriterionOperator operator, int weight) {
        return new TechnicalCriterion(key, key, value, mode, operator, weight);
    }

    private Product product(String id, String temperature, String power, String protection) {
        return new Product(id, "Luminária " + id, "Marca", "Modelo", "Iluminação", "Luminária comercial", null, null,
                Map.of("temperature", temperature, "power", power, "protection", protection),
                new Product.Quote(BigDecimal.TEN, "Fornecedor", LocalDate.now(), "RS"), Instant.now(), Instant.now());
    }
}
