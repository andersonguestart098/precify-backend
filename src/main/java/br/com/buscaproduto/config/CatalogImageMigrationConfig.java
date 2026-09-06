package br.com.buscaproduto.config;

import br.com.buscaproduto.model.CatalogMaterial;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Configuration
public class CatalogImageMigrationConfig {
    @Bean
    CommandLineRunner completeCatalogImageFields(MongoTemplate mongo) {
        return args -> {
            // Equality to null matches both absent and null fields, preserving existing URLs.
            for (String field : new String[] { "imageUrl", "supplierLogoUrl" }) {
                mongo.updateMulti(Query.query(Criteria.where(field).is(null)),
                    new Update().set(field, ""), CatalogMaterial.class);
            }
        };
    }
}
