package br.com.buscaproduto.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import br.com.buscaproduto.exception.ProductNotFoundException;
import br.com.buscaproduto.model.Product;
import br.com.buscaproduto.repository.ProductRepository;

@Service
public class ProductService {
    private final ProductRepository repository;
    private final CatalogService catalogService;

    public ProductService(ProductRepository repository, CatalogService catalogService) {
        this.repository = repository;
        this.catalogService = catalogService;
    }

    public List<Product> findAll() {
        return repository.findAll();
    }

    public Product findById(String id) {
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    public Product updateBasic(String id, String description, BigDecimal quoteValue, String supplier,
            String variationCode, String optionCode) {
        Product current = findById(id);
        List<Product.Variation> variations = new ArrayList<>(current.variations().size());
        boolean updatedVariation = false;

        for (Product.Variation variation : current.variations()) {
            boolean sameVariation = Objects.equals(variation.variationCode(), variationCode)
                    && Objects.equals(normalizeCode(variation.optionCode()), normalizeCode(optionCode));

            if (!updatedVariation && sameVariation) {
                Product.Quote currentQuote = variation.quote();
                Product.Quote updatedQuote = new Product.Quote(
                        quoteValue,
                        supplier.trim(),
                        currentQuote.date(),
                        currentQuote.region());
                variations.add(new Product.Variation(
                        variation.label(),
                        updatedQuote,
                        variation.variationCode(),
                        variation.optionCode()));
                updatedVariation = true;
            } else {
                variations.add(variation);
            }
        }

        if (!updatedVariation) {
            throw new IllegalArgumentException("A opção selecionada deste produto não foi encontrada.");
        }

        Product updated = new Product(
                current.id(), current.name(), current.brand(), current.model(),
                current.category(), current.segment(), current.material(),
                description.trim(), current.imageUrl(), current.supplierLogoUrl(),
                current.attributes(), List.copyOf(variations),
                current.createdAt(), Instant.now(),
                current.materialCode(), current.familyCode(), current.segmentCode());
        return repository.save(updated);
    }

    private String normalizeCode(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public Product save(Product product) {
        var material = catalogService.findAll().stream()
                .filter(item -> item.materialCode().equals(product.materialCode()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Selecione um material válido do catálogo."));
        for (var variation : product.variations()) {
            var definition = material.variations().stream()
                    .filter(item -> item.variationCode().equals(variation.variationCode()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Variação não pertence ao material."));
            if (!definition.options().isEmpty() && definition.options().stream()
                    .noneMatch(option -> option.optionCode().equals(variation.optionCode()))) {
                throw new IllegalArgumentException("Selecione uma opção válida da variação.");
            }
            if (definition.options().isEmpty() && variation.optionCode() != null && !variation.optionCode().isBlank()) {
                throw new IllegalArgumentException("Esta variação não possui opções predefinidas.");
            }
        }
        Instant now = Instant.now();
        Product prepared = new Product(
                product.id(), product.name(), product.brand(), product.model(),
                material.familyName(), material.segmentName(), material.materialName(),
                product.description(), product.imageUrl(), product.supplierLogoUrl(),
                product.attributes(), product.variations(),
                product.createdAt() == null ? now : product.createdAt(), now,
                material.materialCode(), material.familyCode(), material.segmentCode());
        return repository.save(prepared);
    }
}
