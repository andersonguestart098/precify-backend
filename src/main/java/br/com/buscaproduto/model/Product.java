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
        @NotBlank @Indexed String segmentCode,
        @NotBlank @TextIndexed(weight = 2) String segment,
        @NotBlank @Indexed String familyCode,
        @NotBlank @TextIndexed(weight = 2) String category,
        @NotBlank @Indexed String materialCode,
        @NotBlank @TextIndexed(weight = 2) String material,
        @NotBlank @TextIndexed String description,
        String imageUrl,
        String supplierLogoUrl,
        @NotEmpty Map<String, @NotBlank String> attributes,
        @NotEmpty List<@Valid ProductVariation> variations,
        Instant createdAt,
        Instant updatedAt) {

    public record ProductVariation(
            @NotBlank String variationCode,
            String optionCode,
            String label,
            @NotNull @Valid Quote quote) {
    }

    public record Quote(
            @NotNull @PositiveOrZero BigDecimal value,
            @NotBlank String supplier,
            @NotNull LocalDate date,
            @NotBlank String region) {
    }
}
