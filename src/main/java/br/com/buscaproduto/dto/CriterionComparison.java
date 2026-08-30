package br.com.buscaproduto.dto;

import br.com.buscaproduto.enums.CriterionMode;
import br.com.buscaproduto.enums.CriterionOperator;

public record CriterionComparison(
        String key,
        String label,
        String expectedValue,
        String actualValue,
        CriterionMode mode,
        CriterionOperator operator,
        int weight,
        boolean matched) {
}
