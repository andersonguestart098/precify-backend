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
import org.springframework.core.annotation.Order;

import br.com.buscaproduto.model.Product;
import br.com.buscaproduto.repository.ProductRepository;

@Configuration
public class DemoDataConfig {
    public static final String DEFAULT_SUPPLIER_LOGO_URL =
            "https://res.cloudinary.com/dckct1goo/image/upload/v1788072224/logoLumicenter_kilbq0.png";

    @Bean
    @Order(1)
    @ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
    CommandLineRunner seedDemoProducts(ProductRepository repository) {
        return args -> {
            if (repository.count() > 0) return;

            Instant now = Instant.now();
            repository.saveAll(List.of(
                    product("lum-001", "Luminária Linear Pro 40", "Lumicenter", "LLP-40-S",
                            "Luminária LED linear branca para instalação de sobrepor.",
                            Map.of("temperature", "5000 K", "power", "40 W", "installation", "Sobrepor", "protection", "IP65", "cri", "IRC 80"),
                            "349.90", "Lumicenter Sul", LocalDate.of(2026, 8, 28), now),
                    product("lum-002", "Luminária Técnica Line 42", "Stella", "STL-42",
                            "Linha técnica de alta eficiência para ambientes comerciais.",
                            Map.of("temperature", "4000 K", "power", "42 W", "installation", "Sobrepor", "protection", "IP54", "cri", "IRC 80"),
                            "389.50", "Distribuidora Técnica", LocalDate.of(2026, 8, 25), now),
                    product("lum-003", "Painel LED Comercial 40", "Avant", "PLC-40",
                            "Painel LED de embutir para escritórios e áreas comerciais.",
                            Map.of("temperature", "4000 K", "power", "40 W", "installation", "Embutir", "protection", "IP20", "cri", "IRC 80"),
                            "219.90", "Avant Comercial", LocalDate.of(2026, 8, 21), now),
                    product("lum-004", "Luminária Hermética Strong 36", "G-Light", "HST-36",
                            "Luminária vedada para áreas externas e ambientes agressivos.",
                            Map.of("temperature", "3000 K", "power", "36 W", "installation", "Sobrepor", "protection", "IP65", "cri", "IRC 80"),
                            "298.00", "G-Light RS", LocalDate.of(2026, 8, 19), now)
            ));
        };
    }

    private Product product(String id, String name, String brand, String model, String description,
            Map<String, String> attributes, String price, String supplier, LocalDate quoteDate, Instant now) {
        return new Product(id, name, brand, model, "Iluminação", description, null, DEFAULT_SUPPLIER_LOGO_URL, attributes,
                new Product.Quote(new BigDecimal(price), supplier, quoteDate, "RS"), now, now);
    }
}
