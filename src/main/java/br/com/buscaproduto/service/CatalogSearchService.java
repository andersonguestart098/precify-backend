package br.com.buscaproduto.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import br.com.buscaproduto.dto.*;
import br.com.buscaproduto.dto.CatalogSearchPage.*;
import br.com.buscaproduto.model.*;
import br.com.buscaproduto.repository.ProductRepository;

@Service
public class CatalogSearchService {
    private final CatalogService catalog;
    private final ProductRepository products;
    public CatalogSearchService(CatalogService catalog, ProductRepository products) {
        this.catalog = catalog;
        this.products = products;
    }

    public CatalogSearchPage search(SearchRequest request, int page, int size) {
        var materials = catalog.findAll();
        Map<String, List<Product>> byCode = new HashMap<>();
        for (Product product : products.findAll()) {
            String code = product.materialCode();
            if (blank(code)) {
                // Exact full hierarchy only: never guess from old grouped labels or brands.
                var matches = materials.stream().filter(m ->
                        norm(m.segmentName()).equals(norm(product.segment())) &&
                        norm(m.familyName()).equals(norm(product.category())) &&
                        norm(m.materialName()).equals(norm(product.material()))).toList();
                if (matches.size() == 1) code = matches.getFirst().materialCode();
            }
            if (!blank(code)) byCode.computeIfAbsent(code, ignored -> new ArrayList<>()).add(product);
        }
        var filters = new HashMap<String, String>();
        for (TechnicalCriterion criterion : request.criteria()) {
            if (blank(criterion.value())) continue;
            if (!Set.of("segmentCode", "materialCode", "optionCode", "state", "price").contains(criterion.key()))
                throw new IllegalArgumentException("Filtro não suportado: " + criterion.key());
            filters.put(criterion.key(), criterion.value());
        }
        String price = filters.get("price");
        if (price != null) priceMatches(BigDecimal.ZERO, price); // validate even with no offers
        String option = filters.get("optionCode");
        boolean quoteFilter = filters.containsKey("state") || price != null;
        List<Result> results = new ArrayList<>();
        for (CatalogMaterial material : materials) {
            if (!sameOrEmpty(request.familyCode(), material.familyCode()) ||
                    !sameOrEmpty(filters.get("segmentCode"), material.segmentCode()) ||
                    !sameOrEmpty(filters.get("materialCode"), material.materialCode())) continue;
            if (!blank(option) && material.variations().stream().flatMap(v -> v.options().stream())
                    .noneMatch(o -> o.optionCode().equals(option))) continue;
            List<Offer> offers = new ArrayList<>();
            for (Product product : byCode.getOrDefault(material.materialCode(), List.of())) {
                for (var variation : product.variations() == null ? List.<Product.Variation>of() : product.variations()) {
                    var q = variation.quote();
                    if (q == null || q.value() == null || q.value().signum() <= 0 ||
                            blank(q.supplier()) || norm(q.supplier()).contains("a definir")) continue;
                    // All offer filters must match the SAME quote.
                    if (!sameOrEmpty(option, variation.optionCode()) ||
                            !sameOrEmpty(filters.get("state"), q.region()) ||
                            (price != null && !priceMatches(q.value(), price))) continue;
                    offers.add(new Offer(product.id(), product.name(), product.brand(), product.model(),
                            product.imageUrl(), variation.label(), variation.optionCode(), q));
                }
            }
            if (quoteFilter && offers.isEmpty()) continue;
            String haystack = norm(material.materialName() + " " + material.materialCode() + " " +
                    material.familyName() + " " + material.segmentName() + " " +
                    offers.stream().map(o -> o.name() + " " + o.brand() + " " + o.model() + " " + o.quote().supplier())
                            .collect(Collectors.joining(" ")));
            if (!blank(request.query()) && Arrays.stream(norm(request.query()).split("\\s+"))
                    .anyMatch(term -> !haystack.contains(term))) continue;
            offers.sort(Comparator.comparing(o -> o.quote().value()));
            results.add(new Result(material, List.copyOf(offers)));
        }
        results.sort(Comparator.comparing(r -> r.material().materialCode(), CatalogSearchService::compareCodes));
        long offset = (long) page * size;
        int from = (int) Math.min(offset, results.size());
        int to = (int) Math.min(offset + size, results.size());
        return new CatalogSearchPage(List.copyOf(results.subList(from, to)), page, size, results.size(),
                (results.size() + size - 1) / size);
    }

    private static int compareCodes(String a, String b) {
        String[] aa = a.split("\\."), bb = b.split("\\.");
        for (int i = 0; i < Math.min(aa.length, bb.length); i++) {
            int cmp = Integer.compare(Integer.parseInt(aa[i]), Integer.parseInt(bb[i]));
            if (cmp != 0) return cmp;
        }
        return Integer.compare(aa.length, bb.length);
    }
    private static boolean priceMatches(BigDecimal amount, String band) {
        return switch (band) {
            case "Até R$ 100" -> amount.compareTo(new BigDecimal("100")) <= 0;
            case "R$ 100 a R$ 150" -> amount.compareTo(new BigDecimal("100")) >= 0 && amount.compareTo(new BigDecimal("150")) <= 0;
            case "R$ 150 a R$ 200" -> amount.compareTo(new BigDecimal("150")) >= 0 && amount.compareTo(new BigDecimal("200")) <= 0;
            case "Acima de R$ 200" -> amount.compareTo(new BigDecimal("200")) > 0;
            default -> throw new IllegalArgumentException("Faixa de preço inválida.");
        };
    }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static boolean sameOrEmpty(String filter, String value) {
        return blank(filter) || filter.equals(value);
    }
    private static String norm(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
