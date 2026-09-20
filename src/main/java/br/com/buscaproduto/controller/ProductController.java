package br.com.buscaproduto.controller;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.buscaproduto.model.Product;
import br.com.buscaproduto.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public List<Product> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public Product findById(@PathVariable String id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody Product product) {
        Product saved = service.save(product);
        return ResponseEntity.created(URI.create("/api/products/" + saved.id())).body(saved);
    }

    @PatchMapping("/{id}/basic")
    public Product updateBasic(@PathVariable String id, @Valid @RequestBody ProductBasicUpdateRequest request) {
        return service.updateBasic(
                id,
                request.description(),
                request.quoteValue(),
                request.supplier(),
                request.variationCode(),
                request.optionCode());
    }

    public record ProductBasicUpdateRequest(
            @NotBlank String description,
            @NotNull @PositiveOrZero BigDecimal quoteValue,
            @NotBlank String supplier,
            String variationCode,
            String optionCode) {
    }
}
