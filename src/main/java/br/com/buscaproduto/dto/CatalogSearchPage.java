package br.com.buscaproduto.dto;

import java.util.List;
import br.com.buscaproduto.model.CatalogMaterial;
import br.com.buscaproduto.model.Product;

public record CatalogSearchPage(List<Result> content, int page, int size,
        long totalElements, int totalPages) {
    public record Result(CatalogMaterial material, List<Offer> offers) {}
    public record Offer(String productId, String name, String brand, String model,
            String imageUrl, String label, String optionCode, Product.Quote quote, String supplierLogoUrl) {}
}
