package br.com.buscaproduto.dto;

import br.com.buscaproduto.enums.CriterionMode;
import br.com.buscaproduto.enums.CriterionOperator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TechnicalCriterion(
        @NotBlank String key,
        @NotBlank String label,
        @NotBlank String value,
        @NotNull CriterionMode mode,
        @NotNull CriterionOperator operator,
        @Min(1) @Max(100) int weight) {
}
