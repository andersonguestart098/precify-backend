package br.com.buscaproduto.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import br.com.buscaproduto.model.Product;
import br.com.buscaproduto.repository.ProductRepository;

/**
 * One-time import of the real "Aréias" catalog rows from the consolidated
 * price base (segment "Agregados Naturais" / família "Aréias"). Brand,
 * model and supplier are not tracked in the source spreadsheet, so they are
 * filled with an explicit placeholder pending real supplier data.
 */
@Configuration
public class AgregadosNaturaisCatalogSeedConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgregadosNaturaisCatalogSeedConfig.class);
    private static final String SEGMENT = "Agregados Naturais";
    private static final String FAMILY = "Aréias";
    private static final String PLACEHOLDER_BRAND = "Marca a definir";
    private static final String PLACEHOLDER_SUPPLIER = "Fornecedor a definir";
    private static final String REGION = "RS";

    @Bean
    @Order(5)
    CommandLineRunner seedAreiasCatalog(ProductRepository repository) {
        return args -> {
            LocalDate quoteDate = LocalDate.now();
            Instant now = Instant.now();

            List<Product> products = List.of(
                    areiaProduct("areia-fina-jazida", "Areia fina jazida", "1.1.1.1", "Areia fina", "Jazida", now,
                            variation("M³", "92.00", quoteDate),
                            variation("KG", "0", quoteDate)),
                    areiaProduct("areia-fina-varejo", "Areia fina varejo", "1.1.1.2", "Areia fina", "Varejo", now,
                            variation("M³", "175.00", quoteDate),
                            variation("KG", "0", quoteDate)),
                    areiaProduct("areia-media-jazida", "Areia media jazida", "1.1.2.1", "Areia media", "Jazida", now,
                            variation("M³", "90.00", quoteDate),
                            variation("KG", "0", quoteDate)),
                    areiaProduct("areia-media-varejo", "Areia media varejo", "1.1.2.2", "Areia media", "Varejo", now,
                            variation("M³", "168.00", quoteDate),
                            variation("KG", "0", quoteDate)),
                    areiaProduct("areia-grossa-jazida", "Areia grossa jazida", "1.1.3.1", "Areia grossa", "Jazida", now,
                            variation("M²", "79.01", quoteDate),
                            variation("KG", "0", quoteDate)),
                    areiaProduct("areia-grossa-varejo", "Areia grossa varejo", "1.1.3.2", "Areia grossa", "Varejo", now,
                            variation("M³", "180.00", quoteDate),
                            variation("KG", "0", quoteDate)));

            List<Product> missing = products.stream()
                    .filter(product -> repository.findById(product.id()).isEmpty())
                    .toList();

            if (missing.isEmpty()) {
                LOGGER.info("Catálogo de Aréias já importado, nenhum produto novo adicionado.");
                return;
            }

            repository.saveAll(missing);
            LOGGER.info("Catálogo de Aréias importado: {} produtos adicionados.", missing.size());
        };
    }

    private Product areiaProduct(String id, String name, String modelCode, String material, String origem,
            Instant now, Product.Variation... variations) {
        String description = "%s, extraída via %s, comercializada no segmento %s.".formatted(name, origem.toLowerCase(), SEGMENT);
        return new Product(id, name, PLACEHOLDER_BRAND, modelCode, FAMILY, SEGMENT, material,
                description, "", "", Map.of("origem", origem),
                List.of(variations), now, now);
    }

    private Product.Variation variation(String label, String price, LocalDate quoteDate) {
        return new Product.Variation(label, new Product.Quote(new BigDecimal(price), PLACEHOLDER_SUPPLIER, quoteDate, REGION));
    }
}
