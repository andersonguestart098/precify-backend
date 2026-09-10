package br.com.buscaproduto.model;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("catalog_materials")
public record CatalogMaterial(
        @Id String materialCode,
        @Indexed String segmentCode,
        String segmentName,
        @Indexed String familyCode,
        String familyName,
        String materialName,
        String status,
        String observation,
        List<CatalogVariation> variations,
        String imageUrl,
        String supplierLogoUrl) {

    public CatalogMaterial(String materialCode, String segmentCode, String segmentName, String familyCode,
            String familyName, String materialName, String status, String observation, List<CatalogVariation> variations) {
        this(materialCode, segmentCode, segmentName, familyCode, familyName, materialName, status, observation, variations, "", "");
    }

    public record CatalogVariation(
            String variationCode,
            String name,
            String type,
            String requirement,
            int order,
            List<CatalogOption> options) {
    }

    public record CatalogOption(
            String optionCode,
            String name,
            String symbol,
            int order,
            String status) {
    }
}
