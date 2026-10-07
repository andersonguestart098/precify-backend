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

    /** Same material with the image fields owned by the application (never overwritten by catalog syncs). */
    public CatalogMaterial withImages(String imageUrl, String supplierLogoUrl) {
        return new CatalogMaterial(materialCode, segmentCode, segmentName, familyCode, familyName, materialName,
                status, observation, variations, imageUrl, supplierLogoUrl);
    }

    /**
     * A product attribute defined for the material. Since the client's contract V6 a variation also
     * declares how its values are written (format), which units are accepted and whether it is still
     * part of the contract ("ATIVO") or only kept for legacy data ("FORA_DO_CONTRATO").
     */
    public record CatalogVariation(
            String variationCode,
            String name,
            String type,
            String requirement,
            int order,
            List<CatalogOption> options,
            String format,
            List<String> acceptedUnits,
            String identityRule,
            String contractStatus) {

        public CatalogVariation(String variationCode, String name, String type, String requirement, int order,
                List<CatalogOption> options) {
            this(variationCode, name, type, requirement, order, options, null, List.of(), null, null);
        }
    }

    public record CatalogOption(
            String optionCode,
            String name,
            String symbol,
            int order,
            String status) {
    }
}
