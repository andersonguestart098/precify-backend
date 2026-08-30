package br.com.buscaproduto.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record SearchRequest(
        String category,
        String query,
        @NotEmpty List<@Valid TechnicalCriterion> criteria,
        boolean includeAlternatives) {
}
