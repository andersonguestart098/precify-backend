package br.com.buscaproduto.search;

import java.util.List;

import br.com.buscaproduto.product.Product;

public record RankedProduct(
        Product product,
        boolean compatible,
        int compatibility,
        List<CriterionComparison> matches,
        List<CriterionComparison> differences) {
}
