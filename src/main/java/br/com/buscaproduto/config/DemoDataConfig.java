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
    public static final String LEGACY_SUPPLIER_LOGO_URL = "https://res.cloudinary.com/dckct1goo/image/upload/v1788072224/logoLumicenter_kilbq0.png";

    @Bean
    @Order(1)
    @ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
    CommandLineRunner seedDemoProducts(ProductRepository repository) {
        return args -> {
            if (repository.count() > 0) return;

            Instant now = Instant.now();
            repository.saveAll(List.of(
                    product("lum-001", "Luminária Linear Pro 40", "Lumicenter", "LLP-40-S", "Luminária LED linear branca para instalação de sobrepor.", "Eletroeletrônicos e Automação", "Iluminação LED", "Fitas LED (12v/24v/110v), drivers/fontes de alimentação para LED, lâmpadas LED, luminárias spot, painéis de LED (plafons)", null, Map.of("temperature", "5000 K", "power", "40 W"), "349.90", "Lumicenter Sul", LocalDate.of(2026, 8, 28), "RS", now),
                    product("areia-001", "Areia fina jazida", "Base Demo", "AFJ-M3", "Areia fina para aplicações gerais.", "Agregados Naturais", "Aréias", "Areia fina", "M³", Map.of("unidade", "M³"), "92.00", "Fornecedor Demo", LocalDate.of(2026, 8, 28), "RS", now),
                    product("areia-002", "Areia fina jazida", "Base Demo", "AFJ-KG", "Areia fina para aplicações gerais.", "Agregados Naturais", "Aréias", "Areia fina", "KG", Map.of("unidade", "KG"), "92.00", "Fornecedor Demo", LocalDate.of(2026, 8, 28), "RS", now),
                    product("areia-003", "Areia fina varejo", "Base Demo", "AFV-M3", "Areia fina para venda no varejo.", "Agregados Naturais", "Aréias", "Areia fina", "M³", Map.of("unidade", "M³"), "175.00", "Fornecedor Demo", LocalDate.of(2026, 8, 28), "RS", now),
                    product("areia-004", "Areia grossa jazida", "Base Demo", "AGJ-M3", "Areia grossa para aplicações gerais.", "Agregados Naturais", "Aréias", "Areia grossa", "M³", Map.of("unidade", "M³"), "79.01", "Fornecedor Demo", LocalDate.of(2026, 8, 28), "RS", now),
                    product("areia-005", "Areia grossa jazida", "Base Demo", "AGJ-KG", "Areia grossa para aplicações gerais.", "Agregados Naturais", "Aréias", "Areia grossa", "KG", Map.of("unidade", "KG"), "79.01", "Fornecedor Demo", LocalDate.of(2026, 8, 28), "RS", now)
            ));
        };
    }

    private Product product(String id, String name, String brand, String model, String description,
            String segment, String family, String material, String variation,
            Map<String, String> attributes, String price, String supplier, LocalDate quoteDate,
            String region, Instant now) {
        Product.Quote quote = new Product.Quote(new BigDecimal(price), supplier, quoteDate, region);
        return new Product(id, name, brand, model, family, segment, material,
                description, "", "", attributes,
                List.of(new Product.Variation(variation, quote)), now, now);
    }
}
