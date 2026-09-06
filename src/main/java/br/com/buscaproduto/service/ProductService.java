package br.com.buscaproduto.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import br.com.buscaproduto.exception.ProductNotFoundException;
import br.com.buscaproduto.model.Product;
import br.com.buscaproduto.repository.ProductRepository;

@Service
public class ProductService {
    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public List<Product> findAll() {
        return repository.findAll();
    }

    public Product findById(String id) {
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    public Product save(Product product) {
        Instant now = Instant.now();
        Product prepared = new Product(
                product.id(), product.name(), product.brand(), product.model(),
                product.category(), product.segment(), product.material(),
                product.description(), product.imageUrl(), product.supplierLogoUrl(),
                product.attributes(), product.variations(),
                product.createdAt() == null ? now : product.createdAt(), now);
        return repository.save(prepared);
    }
}
