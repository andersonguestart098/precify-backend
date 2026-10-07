package br.com.buscaproduto.model;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Audit log of each catalog sync (materials + products) applied to the database. */
@Document("catalog_imports")
public record CatalogImport(
        @Id String id,
        String batchId,
        String sourceFile,
        String sourceSha256,
        @Indexed String productsSha256,
        String status,
        Instant startedAt,
        Instant finishedAt,
        Report report,
        List<String> errors) {

    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    public record Report(
            int materialsInBundle, int materialsInserted, int materialsUpdated, int materialsUnchanged,
            int materialsOnlyInDatabase,
            int productsInBundle, int productsInserted, int productsUpdated, int productsUnchanged,
            int productsWithdrawn, int skusInBundle) {
    }
}
