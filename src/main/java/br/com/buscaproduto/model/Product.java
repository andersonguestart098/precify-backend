package br.com.buscaproduto.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
        @NotBlank @TextIndexed String description,
        @NotEmpty Map<String, @NotBlank String> attributes,
        @NotNull @Valid Quote quote,
        Instant createdAt,
        Instant updatedAt) {

    public record Quote(
            @NotNull @PositiveOrZero BigDecimal value,
            @NotBlank String supplier,
            @NotNull LocalDate date,
            @NotBlank String region) {
    }
}
