package br.com.buscaproduto.dto;

import java.util.List;

import br.com.buscaproduto.model.Product;

public record RankedProduct(
        Product product,
        boolean compatible,
        int compatibility,
        List<CriterionComparison> matches,
        List<CriterionComparison> differences) {
}
