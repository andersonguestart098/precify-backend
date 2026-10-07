package br.com.buscaproduto.model;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Catalog product (level 4 of the hierarchy: Segment > Family > Material > Product > SKU).
 *
 * Imported from the client's PRICO workbook (sheet 37_CATALOGO_APTO). These are documented
 * products, not offers: there is no price, stock or supplier here. Commercial quotes stay in
 * the {@code products} collection ({@link Product}).
 *
 * Fields filled by the import are replaced on every sync; {@code imageUrl} and {@code createdAt}
 * belong to the application and are preserved. Products that disappear from a later delivery are
 * kept with {@code active=false}.
 */
@Document("catalog_products")
@CompoundIndexes({
        @CompoundIndex(name = "material_active", def = "{'materialCode': 1, 'active': 1}"),
        @CompoundIndex(name = "sku_code_unique", def = "{'skus.skuCode': 1}", unique = true),
        @CompoundIndex(name = "sku_gtin", def = "{'skus.gtin': 1}"),
        @CompoundIndex(name = "sku_manufacturer", def = "{'skus.manufacturerSku': 1}"),
        @CompoundIndex(name = "brand", def = "{'brand': 1}")
})
public record CatalogProduct(
        @Id String productCode,
        String sourceCode,
        String materialCode,
        String familyCode,
        String segmentCode,
        String name,
        String brand,
        String manufacturer,
        String model,
        String fingerprint,
        String sourceUrl,
        String documentalCoverage,
        String consultedAt,
        Origin origin,
        List<Sku> skus,
        String importBatch,
        String contentHash,
        Boolean active,
        String imageUrl,
        Instant createdAt,
        Instant importedAt,
        Instant withdrawnAt) {

    /** Copy with the fields owned by the importer/application (everything the bundle does not carry). */
    public CatalogProduct withSyncState(String contentHash, boolean active, String imageUrl, Instant createdAt,
            Instant importedAt) {
        return new CatalogProduct(productCode, sourceCode, materialCode, familyCode, segmentCode, name, brand,
                manufacturer, model, fingerprint, sourceUrl, documentalCoverage, consultedAt, origin, skus,
                importBatch, contentHash, active, imageUrl, createdAt, importedAt, null);
    }

    /** Where the record came from in the client's consolidation (traceability only). */
    public record Origin(String union, String file, Integer row) {
    }

    /** Sellable presentation of the product (level 5). */
    public record Sku(
            String skuCode,
            String sourceCode,
            String manufacturerSku,
            String gtin,
            String commercialUnit,
            String commercialUnitDetail,
            String presentation,
            String sourceUrl,
            String consultedAt,
            String commercialMode,
            String commercialIdentityKey,
            List<String> validatedContents,
            List<String> pendingVariationCodes,
            List<SkuValue> values) {
    }

    /**
     * Documented value of one catalog variation for the SKU. {@code value} is the display text
     * (pt-BR decimals); {@code numericValue}/{@code dimensions} are filled when the contract format is
     * numeric or dimensional; {@code optionCode} links to a predefined catalog option when it matches.
     */
    public record SkuValue(
            String variationCode,
            String attribute,
            String value,
            Double numericValue,
            List<Double> dimensions,
            String unit,
            String optionCode,
            String qualifier,
            String originalValue,
            String sourceUrl) {
    }
}
