package br.com.buscaproduto.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import br.com.buscaproduto.dto.CriterionComparison;
import br.com.buscaproduto.dto.RankedProduct;
import br.com.buscaproduto.dto.SearchRequest;
import br.com.buscaproduto.dto.TechnicalCriterion;
import br.com.buscaproduto.enums.CriterionMode;
import br.com.buscaproduto.enums.CriterionOperator;
import br.com.buscaproduto.model.Product;

@Service
public class CompatibilityService {
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(?:[.,]\\d+)?");

    public List<RankedProduct> rank(List<Product> products, SearchRequest request) {
        int totalWeight = request.criteria().stream().mapToInt(TechnicalCriterion::weight).sum();

        return products.stream()
                .map(product -> evaluate(product, request.criteria(), totalWeight, request.query()))
                .filter(result -> result.textMatched()
                        && (result.ranked().compatible() || request.includeAlternatives()))
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
        TextMatch textMatch = textMatch(product, query);
        return new Evaluation(
                new RankedProduct(product, compatible, compatibility, matches, differences),
                textMatch.score(),
                textMatch.matched());
    }

    private boolean matches(String actual, String expected, CriterionOperator operator) {
        if (operator == CriterionOperator.EXACT) {
            return normalize(actual).equals(normalize(expected));
        }
        Double actualNumber = extractNumber(actual);
        Double expectedNumber = extractNumber(expected);
        if (actualNumber == null || expectedNumber == null) return false;
        return operator == CriterionOperator.MINIMUM ? actualNumber >= expectedNumber : actualNumber <= expectedNumber;
    }

    private TextMatch textMatch(Product product, String query) {
        if (query == null || query.isBlank()) return new TextMatch(true, 0);

        String name = normalize(product.name());
        String brandAndModel = normalize(product.brand() + " " + product.model());
        String description = normalize(product.description());
        String attributes = normalize(product.attributes().entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .collect(Collectors.joining(" ")));

        int score = 0;
        for (String term : normalize(query).split(" ")) {
            if (term.length() < 2) continue;

            int termScore = 0;
            if (attributes.contains(term)) termScore += 20;
            if (name.contains(term)) termScore += 15;
            if (brandAndModel.contains(term)) termScore += 10;
            if (description.contains(term)) termScore += 5;

            if (termScore == 0) return new TextMatch(false, 0);
            score += termScore;
        }
        return new TextMatch(true, score);
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.forLanguageTag("pt-BR"))
                .replaceAll("(?<=\\d)\\s+(?=(k|w|v|mm|cm|m|kg|lm)\\b)", "")
                .replaceAll("\\b(ip|irc)\\s+(?=\\d)", "$1")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Double extractNumber(String value) {
        Matcher matcher = NUMBER_PATTERN.matcher(value);
        return matcher.find() ? Double.valueOf(matcher.group().replace(',', '.')) : null;
    }

    private record TextMatch(boolean matched, int score) {
    }

    private record Evaluation(RankedProduct ranked, int textScore, boolean textMatched) {
    }
}
