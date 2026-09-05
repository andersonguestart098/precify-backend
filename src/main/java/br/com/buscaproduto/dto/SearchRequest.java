package br.com.buscaproduto.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record SearchRequest(
        String category,
        String query,
        @NotNull List<@Valid TechnicalCriterion> criteria,
        boolean includeAlternatives) {
}
