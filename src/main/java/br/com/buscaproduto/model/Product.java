package br.com.buscaproduto.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Document("products")
public record Product(
        @Id String id,
        @NotBlank @TextIndexed(weight = 5) String name,
        @NotBlank @TextIndexed(weight = 3) String brand,
        @NotBlank @TextIndexed(weight = 3) String model,
        @NotBlank @Indexed @TextIndexed(weight = 2) String category,
        @NotBlank @Indexed @TextIndexed(weight = 2) String segment,
        @NotBlank @Indexed @TextIndexed(weight = 2) String material,
        @NotBlank @TextIndexed String description,
        String imageUrl,
        String supplierLogoUrl,
        @NotEmpty Map<String, @NotBlank String> attributes,
        @NotEmpty @Valid List<Variation> variations,
        Instant createdAt,
        Instant updatedAt,
        @Indexed String materialCode,
        @Indexed String familyCode,
        @Indexed String segmentCode) {

    // Keep legacy seeds and persisted documents readable without guessing codes.
    public Product(String id, String name, String brand, String model, String category,
            String segment, String material, String description, String imageUrl,
            String supplierLogoUrl, Map<String, String> attributes, List<Variation> variations,
            Instant createdAt, Instant updatedAt) {
        this(id, name, brand, model, category, segment, material, description, imageUrl,
                supplierLogoUrl, attributes, variations, createdAt, updatedAt, null, null, null);
    }

    public record Variation(
            @Indexed @TextIndexed(weight = 2) String label,
            @NotNull @Valid Quote quote,
            String variationCode,
            String optionCode) {
        public Variation(String label, Quote quote) {
            this(label, quote, null, null);
        }
    }

    public record Quote(
            @NotNull @PositiveOrZero BigDecimal value,
            @NotBlank String supplier,
            @NotNull LocalDate date,
            @NotBlank String region) {
    }
}
