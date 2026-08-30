package br.com.buscaproduto.search;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import br.com.buscaproduto.product.Product;

@Component
public class CompatibilityEngine {
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(?:[.,]\\d+)?");

    public List<RankedProduct> rank(List<Product> products, SearchRequest request) {
        int totalWeight = request.criteria().stream().mapToInt(TechnicalCriterion::weight).sum();

        return products.stream()
                .map(product -> evaluate(product, request.criteria(), totalWeight, request.query()))
                .filter(result -> result.ranked().compatible() || request.includeAlternatives())
                .sorted(Comparator
                        .comparing((Evaluation result) -> result.ranked().compatible()).reversed()
                        .thenComparing(Comparator.comparingInt((Evaluation result) -> result.ranked().compatibility()).reversed())
                        .thenComparing(Evaluation::textScore, Comparator.reverseOrder()))
                .map(Evaluation::ranked)
                .toList();
    }

    private Evaluation evaluate(Product product, List<TechnicalCriterion> criteria, int totalWeight, String query) {
        List<CriterionComparison> matches = new ArrayList<>();
        List<CriterionComparison> differences = new ArrayList<>();
        int matchedWeight = 0;

        for (TechnicalCriterion criterion : criteria) {
            String actual = product.attributes().getOrDefault(criterion.key(), "Não informado");
            boolean matched = matches(actual, criterion.value(), criterion.operator());
            CriterionComparison comparison = new CriterionComparison(
                    criterion.key(), criterion.label(), criterion.value(), actual,
                    criterion.mode(), criterion.operator(), criterion.weight(), matched);
            if (matched) {
                matches.add(comparison);
                matchedWeight += criterion.weight();
            } else {
                differences.add(comparison);
            }
        }

        boolean compatible = differences.stream().noneMatch(item -> item.mode() == CriterionMode.REQUIRED);
        int compatibility = totalWeight == 0 ? 0 : Math.round(matchedWeight * 100f / totalWeight);
        return new Evaluation(new RankedProduct(product, compatible, compatibility, matches, differences), textScore(product, query));
    }

    private boolean matches(String actual, String expected, CriterionOperator operator) {
        if (operator == CriterionOperator.EXACT) {
            return normalize(actual).equals(normalize(expected));
        }
        Double actualNumber = extractNumber(actual);
        Double expectedNumber = extractNumber(expected);
        if (actualNumber == null || expectedNumber == null) return false;
        return operator == CriterionOperator.MINIMUM
                ? actualNumber >= expectedNumber
                : actualNumber <= expectedNumber;
    }

    private int textScore(Product product, String query) {
        if (query == null || query.isBlank()) return 0;
        String name = normalize(product.name());
        String brandAndModel = normalize(product.brand() + " " + product.model());
        String description = normalize(product.description());
        int score = 0;
        for (String term : normalize(query).split(" ")) {
            if (term.length() < 3) continue;
            if (name.contains(term)) score += 4;
            if (brandAndModel.contains(term)) score += 3;
            if (description.contains(term)) score += 1;
        }
        return score;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.forLanguageTag("pt-BR"))
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Double extractNumber(String value) {
        Matcher matcher = NUMBER_PATTERN.matcher(value);
        return matcher.find() ? Double.valueOf(matcher.group().replace(',', '.')) : null;
    }

    private record Evaluation(RankedProduct ranked, int textScore) {}
}
