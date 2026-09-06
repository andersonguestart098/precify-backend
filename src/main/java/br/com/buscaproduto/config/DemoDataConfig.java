package br.com.buscaproduto.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.buscaproduto.model.Product;
import br.com.buscaproduto.repository.ProductRepository;

@Configuration
public class DemoDataConfig {
    public static final String LEGACY_SUPPLIER_LOGO_URL = "https://res.cloudinary.com/dckct1goo/image/upload/v1788072224/logoLumicenter_kilbq0.png";

    @Bean
    @ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
    CommandLineRunner seedDemoProducts(ProductRepository repository) {
        return args -> {
            if (repository.count() > 0) return;
            Instant now = Instant.now();
            repository.save(new Product("areia-001", "Areia fina natural", "Base Demo", "AFN-M3",
                    "1", "Agregados, Solos e Minerais", "1.1", "Areias", "1.1.1", "Areia fina natural",
                    "Produto demonstrativo alinhado ao catálogo PRICO.", "", "", Map.of("unidade", "m³"),
                    List.of(new Product.ProductVariation("1.1.1.V01", "1.1.1.V01.001", "m³",
                            new Product.Quote(new BigDecimal("92.00"), "Fornecedor Demo",
                                    LocalDate.of(2026, 8, 28), "RS"))),
                    now, now));
        };
    }
}
