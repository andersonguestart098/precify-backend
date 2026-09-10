package br.com.buscaproduto.config;

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

import com.mongodb.client.result.UpdateResult;

import br.com.buscaproduto.model.Product;

@Configuration
public class ProductImageMigrationConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductImageMigrationConfig.class);

    @Bean
    @Order(2)
    CommandLineRunner completeExistingProductImages(MongoTemplate mongoTemplate) {
        return args -> {
            Query missingProductImage = new Query(new Criteria().orOperator(
                    Criteria.where("imageUrl").exists(false),
                    Criteria.where("imageUrl").is(null)));

            Query missingSupplierLogo = new Query(new Criteria().orOperator(
                    Criteria.where("supplierLogoUrl").exists(false),
                    Criteria.where("supplierLogoUrl").is(null),
                    Criteria.where("supplierLogoUrl").is(DemoDataConfig.LEGACY_SUPPLIER_LOGO_URL)));

            UpdateResult productImages = mongoTemplate.updateMulti(
                    missingProductImage, new Update().set("imageUrl", ""), Product.class);
            UpdateResult supplierLogos = mongoTemplate.updateMulti(
                    missingSupplierLogo, new Update().set("supplierLogoUrl", ""), Product.class);

            LOGGER.info("Migração de imagens concluída: {} imageUrl e {} supplierLogoUrl atualizados.",
                    productImages.getModifiedCount(), supplierLogos.getModifiedCount());
        };
    }
}
