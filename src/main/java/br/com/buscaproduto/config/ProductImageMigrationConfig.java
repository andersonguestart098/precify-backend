package br.com.buscaproduto.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import br.com.buscaproduto.model.Product;
import br.com.buscaproduto.repository.ProductRepository;

@Configuration
public class ProductImageMigrationConfig {
    @Bean
    @Order(2)
    CommandLineRunner completeExistingProductImages(ProductRepository repository) {
        return args -> {
            List<Product> productsToUpdate = repository.findAll().stream()
                    .filter(product -> product.supplierLogoUrl() == null || product.supplierLogoUrl().isBlank())
                    .map(product -> new Product(
                            product.id(), product.name(), product.brand(), product.model(), product.category(),
                            product.description(), product.imageUrl(), DemoDataConfig.DEFAULT_SUPPLIER_LOGO_URL,
                            product.attributes(), product.quote(), product.createdAt(), product.updatedAt()))
                    .toList();

            if (!productsToUpdate.isEmpty()) {
                repository.saveAll(productsToUpdate);
            }
        };
    }
}
