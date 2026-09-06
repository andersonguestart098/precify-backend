package br.com.buscaproduto.config;

import java.util.List;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.UpdateResult;

import br.com.buscaproduto.model.Product;

/**
 * Backfills legacy lighting products created before segment/material were part of
 * the catalog hierarchy. Aligns them with the consolidated taxonomy (segment
 * "Eletroeletrônicos e Automação" / família "Iluminação LED").
 */
@Configuration
public class ProductCatalogMigrationConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductCatalogMigrationConfig.class);
    private static final String LEGACY_LIGHTING_CATEGORY = "Iluminação";
    private static final String LIGHTING_SEGMENT = "Eletroeletrônicos e Automação";
    private static final String LIGHTING_FAMILY = "Iluminação LED";
    private static final String LIGHTING_MATERIAL =
            "Fitas LED (12v/24v/110v), drivers/fontes de alimentação para LED, lâmpadas LED, luminárias spot, painéis de LED (plafons)";

    @Bean
    @Order(3)
    CommandLineRunner alignLightingProductsWithCatalogStandard(MongoTemplate mongoTemplate) {
        return args -> {
            Query legacyLightingProducts = new Query(Criteria.where("category").is(LEGACY_LIGHTING_CATEGORY));
            Update update = new Update()
                    .set("category", LIGHTING_FAMILY)
                    .set("segment", LIGHTING_SEGMENT)
                    .set("material", LIGHTING_MATERIAL);

            UpdateResult result = mongoTemplate.updateMulti(legacyLightingProducts, update, Product.class);
            LOGGER.info("Migração de catálogo concluída: {} produtos de iluminação alinhados ao padrão consolidado.",
                    result.getModifiedCount());
        };
    }

    /**
     * Products created before variations existed store a single top-level
     * "quote"/"variation" pair instead of the "variations" array. Folds that
     * legacy pair into a one-item variations list and drops the old fields.
     */
    @Bean
    @Order(4)
    CommandLineRunner restructureLegacyProductVariations(MongoTemplate mongoTemplate) {
        return args -> {
            MongoCollection<Document> products = mongoTemplate.getCollection("products");
            List<Document> pipeline = List.of(
                    new Document("$set", new Document("variations", List.of(
                            new Document("label", "$variation").append("quote", "$quote")))),
                    new Document("$unset", List.of("quote", "variation")));

            var result = products.updateMany(Filters.exists("quote"), pipeline);
            LOGGER.info("Migração de variações concluída: {} produtos reestruturados para o formato de lista.",
                    result.getModifiedCount());
        };
    }
}
