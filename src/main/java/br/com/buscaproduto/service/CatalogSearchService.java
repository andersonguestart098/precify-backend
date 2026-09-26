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
        return search(request, page, size, null);
    }

    public CatalogSearchPage search(SearchRequest request, int page, int size, Set<String> allowedCodes) {
        var materials = catalog.findAll();
        Map<String, List<Product>> byCode = productsByCode(materials);
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
            if (allowedCodes != null && !allowedCodes.contains(material.materialCode())) continue;
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
                    if (!sameOrEmpty(option, variation.optionCode()) ||
                            !sameOrEmpty(filters.get("state"), q.region()) ||
                            (price != null && !priceMatches(q.value(), price))) continue;
                    offers.add(new Offer(product.id(), product.name(), product.brand(), product.model(),
                            product.imageUrl(), variation.label(), variation.optionCode(), q, product.supplierLogoUrl()));
                }
            }
            if (quoteFilter && offers.isEmpty()) continue;
            String haystack = norm(material.materialName() + " " + material.materialCode() + " " +
                    material.familyName() + " " + material.segmentName() + " " +
                    offers.stream().map(o -> o.name() + " " + o.brand() + " " + o.model() + " " + o.quote().supplier())
                            .collect(Collectors.joining(" ")));
            if (!blank(request.query()) && Arrays.stream(norm(request.query()).split("\\s+"))
                    .anyMatch(term -> !haystack.contains(term))) continue;
            offers.sort(Comparator.comparingInt(CatalogSearchService::offerCompleteness).reversed()
                    .thenComparing(o -> o.quote().value()).thenComparing(Offer::productId));
            Product preview = offers.isEmpty() ? byCode.getOrDefault(material.materialCode(), List.of()).stream()
                    .filter(p -> blank(option) || (p.variations() != null && p.variations().stream()
                        .anyMatch(v -> option.equals(v.optionCode()))))
                    .max(Comparator.comparingInt(p -> (!blank(p.imageUrl()) ? 2 : 0)
                        + (!blank(p.supplierLogoUrl()) ? 1 : 0))).orElse(null) : null;
            String image = offers.isEmpty() ? (preview == null ? null : preview.imageUrl()) : offers.getFirst().imageUrl();
            String logo = offers.isEmpty() ? (preview == null ? null : preview.supplierLogoUrl()) : offers.getFirst().supplierLogoUrl();
            if (blank(image)) image = material.imageUrl();
            if (offers.isEmpty() && blank(logo)) logo = material.supplierLogoUrl();
            results.add(new Result(material, List.copyOf(offers), image, logo));
        }
        results.sort(Comparator
                .comparing((Result r) -> r.material().segmentCode(), CatalogSearchService::compareCodes)
                .thenComparing(r -> r.material().familyCode(), CatalogSearchService::compareCodes)
                .thenComparing(r -> r.material().materialCode(), CatalogSearchService::compareCodes));
        int featuredCount = 0;
        for (int i = 0; i < results.size() && featuredCount < 4; i++) {
            Result r = results.get(i);
            if (eligibleHighlight(r)) {
                results.set(i, new Result(r.material(), r.offers(), r.imageUrl(), r.supplierLogoUrl(), true));
                featuredCount++;
            }
        }
        long offset = (long) page * size;
        int from = (int) Math.min(offset, results.size());
        int to = (int) Math.min(offset + size, results.size());
        return new CatalogSearchPage(List.copyOf(results.subList(from, to)), page, size, results.size(),
                (results.size() + size - 1) / size);
    }

    public record Detail(CatalogMaterial material, List<Product> products) {}
    public Detail detail(String code) {
        var materials = catalog.findAll();
        var material = materials.stream().filter(m -> m.materialCode().equals(code)).findFirst()
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Produto não encontrado."));
        return new Detail(material, List.copyOf(productsByCode(materials).getOrDefault(code, List.of())));
    }

    private Map<String, List<Product>> productsByCode(List<CatalogMaterial> materials) {
        Map<String, List<CatalogMaterial>> byNames = materials.stream().collect(Collectors.groupingBy(
                m -> hierarchy(m.segmentName(), m.familyName(), m.materialName())));
        Map<String, List<Product>> byCode = new HashMap<>();
        for (Product product : products.findAll()) {
            String code = product.materialCode();
            if (blank(code)) {
                var matches = byNames.getOrDefault(
                        hierarchy(product.segment(), product.category(), product.material()), List.of());
                if (matches.size() == 1) code = matches.get(0).materialCode();
            }
            if (!blank(code)) byCode.computeIfAbsent(code, ignored -> new ArrayList<>()).add(product);
        }
        return byCode;
    }

    static int offerCompleteness(Offer offer) {
        return (!blank(offer.imageUrl()) ? 1000 : 0)
            + (!blank(offer.optionCode()) ? 100 : 0)
            + (!blank(offer.supplierLogoUrl()) ? 20 : 0)
            + (!blank(offer.label()) ? 2 : 0) + (!blank(offer.brand()) ? 1 : 0);
    }
    static boolean eligibleHighlight(Result result) {
        String status = norm(result.material().status()).replace('_', ' ');
        return !status.isBlank() && !status.equals("em revisao") && hasQuotedOption(result);
    }
    static boolean hasQuotedOption(Result result) {
        return result.offers().stream().anyMatch(o -> !blank(o.optionCode())
            && result.material().variations().stream().flatMap(v -> v.options().stream())
                .anyMatch(option -> o.optionCode().equals(option.optionCode())));
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
        if (band.contains(":")) {
            String[] parts = band.split(":", -1);
            if (parts.length != 2) throw new IllegalArgumentException("Faixa de preço inválida.");
            try {
                BigDecimal min = parts[0].isBlank() ? null : new BigDecimal(parts[0].replace(',', '.'));
                BigDecimal max = parts[1].isBlank() ? null : new BigDecimal(parts[1].replace(',', '.'));
                if (min == null && max == null) throw new IllegalArgumentException("Faixa de preço inválida.");
                if ((min != null && min.signum() < 0) || (max != null && max.signum() < 0)
                        || (min != null && max != null && min.compareTo(max) > 0))
                    throw new IllegalArgumentException("Faixa de preço inválida.");
                return (min == null || amount.compareTo(min) >= 0) && (max == null || amount.compareTo(max) <= 0);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Faixa de preço inválida.");
            }
        }
        return switch (band) {
            case "Até R$ 100" -> amount.compareTo(new BigDecimal("100")) <= 0;
            case "R$ 100 a R$ 150" -> amount.compareTo(new BigDecimal("100")) >= 0 && amount.compareTo(new BigDecimal("150")) <= 0;
            case "R$ 150 a R$ 200" -> amount.compareTo(new BigDecimal("150")) >= 0 && amount.compareTo(new BigDecimal("200")) <= 0;
            case "Acima de R$ 200" -> amount.compareTo(new BigDecimal("200")) > 0;
            default -> throw new IllegalArgumentException("Faixa de preço inválida.");
        };
    }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String hierarchy(String segment, String family, String material) {
        return norm(segment) + "|" + norm(family) + "|" + norm(material);
    }
    private static boolean sameOrEmpty(String filter, String value) {
        return blank(filter) || filter.equals(value);
    }
    private static String norm(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
