package br.com.buscaproduto.search;

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
