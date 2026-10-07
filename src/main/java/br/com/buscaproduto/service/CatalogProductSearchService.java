package br.com.buscaproduto.service;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.buscaproduto.dto.CatalogSearchPage;
import br.com.buscaproduto.dto.ProductSearchPage;
import br.com.buscaproduto.dto.ProductSearchRequest;
import br.com.buscaproduto.dto.SearchRequest;
import br.com.buscaproduto.model.CatalogMaterial;
import br.com.buscaproduto.model.CatalogProduct;
import br.com.buscaproduto.repository.CatalogProductRepository;

/**
 * Search over catalog products (last level). Same rules as the material search: exact code
 * filters, every free-text term must be present (accent/case insensitive) and results are
 * ranked by relevance, then by how complete the record is, then by natural code order.
 *
 * The product is the last level and its parent is the material: a material that has no active
 * product is listed as the material itself, so nothing disappears from the screen. Ties go to the
 * product (it is the more specific result).
 *
 * The active products are kept in memory (about 9 thousand documents) and refreshed every
 * few minutes in background, so a search never scans MongoDB. A catalog sync invalidates it.
 */
@Service
public class CatalogProductSearchService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogProductSearchService.class);
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int MAX_BRANDS = 300;

    private final CatalogProductRepository repository;
    private final CatalogService catalog;
    private final CatalogSearchService materialSearch;
    private final AtomicBoolean reloading = new AtomicBoolean();
    private volatile Index index;

    public CatalogProductSearchService(CatalogProductRepository repository, CatalogService catalog,
            CatalogSearchService materialSearch) {
        this.repository = repository;
        this.catalog = catalog;
        this.materialSearch = materialSearch;
    }

    record Entry(CatalogProduct product, CatalogMaterial material, String brandKey, String strong, String haystack,
            List<String> hierarchy, Set<String> codes, int completeness) {
    }

    /** Material with no active product: it stands in for its missing children in the listing. */
    record MaterialEntry(CatalogMaterial material, String haystack, List<String> hierarchy) {
    }

    record Index(List<Entry> entries, List<MaterialEntry> childless, Instant loadedAt) {
    }

    /** Next search reloads products and catalog names from the database. */
    public void invalidate() {
        index = null;
    }

    public ProductSearchPage search(ProductSearchRequest request, int page, int size, Set<String> allowedCodes) {
        List<String> terms = terms(request.query());
        String query = norm(request.query());
        String brand = norm(request.brand());
        String material = trim(request.materialCode());

        Index current = index();
        List<Entry> scoped = new ArrayList<>();
        for (Entry entry : current.entries()) {
            CatalogProduct product = entry.product();
            if (allowedCodes != null && !allowedCodes.contains(product.productCode())) continue;
            if (!sameOrEmpty(request.segmentCode(), product.segmentCode())
                    || !sameOrEmpty(request.familyCode(), product.familyCode())) continue;
            if (terms.stream().anyMatch(term -> !entry.haystack().contains(term))) continue;
            scoped.add(entry);
        }

        Map<String, Long> materialCounts = new TreeMap<>(CatalogProductSearchService::compareCodes);
        Map<String, Long> brandCounts = new LinkedHashMap<>();
        Map<String, String> brandNames = new LinkedHashMap<>();
        List<Entry> results = new ArrayList<>();
        for (Entry entry : scoped) {
            boolean materialOk = material.isEmpty() || material.equals(entry.product().materialCode());
            boolean brandOk = brand.isEmpty() || brand.equals(entry.brandKey());
            if (brandOk) materialCounts.merge(entry.product().materialCode(), 1L, Long::sum);
            if (materialOk && !entry.brandKey().isEmpty()) {
                brandCounts.merge(entry.brandKey(), 1L, Long::sum);
                brandNames.putIfAbsent(entry.brandKey(), entry.product().brand());
            }
            if (materialOk && brandOk) results.add(entry);
        }

        // A brand or the favorites list only exist at product level, so childless materials are left out then.
        List<MaterialEntry> materials = new ArrayList<>();
        if (brand.isEmpty() && allowedCodes == null) {
            for (MaterialEntry entry : current.childless()) {
                CatalogMaterial m = entry.material();
                if (!sameOrEmpty(request.segmentCode(), m.segmentCode()) || !sameOrEmpty(request.familyCode(), m.familyCode())
                        || !(material.isEmpty() || material.equals(m.materialCode()))) continue;
                if (terms.stream().anyMatch(term -> !entry.haystack().contains(term))) continue;
                materials.add(entry);
            }
        }

        record Scored(Entry product, MaterialEntry material, String code, int relevance, int completeness) {}
        List<Scored> ranked = new ArrayList<>(results.size() + materials.size());
        for (Entry entry : results)
            ranked.add(new Scored(entry, null, entry.product().productCode(), relevance(entry, terms, query), entry.completeness()));
        for (MaterialEntry entry : materials)
            ranked.add(new Scored(null, entry, entry.material().materialCode(), materialRelevance(entry, terms, query), -1));
        ranked.sort(Comparator.comparingInt(Scored::relevance).reversed()
                .thenComparing(Comparator.comparingInt(Scored::completeness).reversed())
                .thenComparing(Scored::code, CatalogProductSearchService::compareCodes));

        int from = (int) Math.min((long) page * size, ranked.size());
        int to = (int) Math.min((long) from + size, ranked.size());
        List<Scored> slice = ranked.subList(from, to);
        Map<String, CatalogSearchPage.Result> materialCards = materialCards(slice.stream()
                .filter(scored -> scored.material() != null).map(Scored::code).collect(Collectors.toSet()));
        List<ProductSearchPage.Result> content = slice.stream().map(scored -> {
            if (scored.material() != null) {
                CatalogMaterial m = scored.material().material();
                return new ProductSearchPage.Result(ProductSearchPage.MATERIAL, null, m.segmentName(), m.familyName(),
                        m.materialName(), blankToNull(m.imageUrl()),
                        materialCards.getOrDefault(m.materialCode(), new CatalogSearchPage.Result(m, List.of())));
            }
            CatalogMaterial m = scored.product().material();
            return new ProductSearchPage.Result(ProductSearchPage.PRODUCT, scored.product().product(),
                    m == null ? null : m.segmentName(), m == null ? null : m.familyName(),
                    m == null ? null : m.materialName(), m == null ? null : blankToNull(m.imageUrl()), null);
        }).toList();
        List<ProductSearchPage.Facet> brands = brandCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(MAX_BRANDS)
                .map(e -> new ProductSearchPage.Facet(brandNames.get(e.getKey()), e.getValue()))
                .toList();
        return new ProductSearchPage(content, page, size, ranked.size(), (ranked.size() + size - 1) / size,
                results.size(), materials.size(), brands, materialCounts);
    }

    /** Same card the material view shows (offers, image, supplier logo), only for materials on this page. */
    private Map<String, CatalogSearchPage.Result> materialCards(Set<String> codes) {
        if (codes.isEmpty()) return Map.of();
        return materialSearch.search(new SearchRequest(null, null, List.of(), false), 0, codes.size(), codes).content().stream()
                .collect(Collectors.toMap(result -> result.material().materialCode(), Function.identity(), (a, b) -> a));
    }

    // ------------------------------------------------------------------------------------------

    private Index index() {
        Index current = index;
        if (current == null) {
            synchronized (this) {
                if (index == null) index = load();
                return index;
            }
        }
        if (current.loadedAt().plus(TTL).isBefore(Instant.now()) && reloading.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> {
                try {
                    index = load();
                } catch (RuntimeException exception) {
                    LOGGER.warn("Falha ao recarregar o índice de produtos; mantendo o anterior.", exception);
                } finally {
                    reloading.set(false);
                }
            });
        }
        return current;
    }

    private Index load() {
        Map<String, CatalogMaterial> materials = catalog.findAll().stream()
                .collect(Collectors.toMap(CatalogMaterial::materialCode, Function.identity(), (a, b) -> a));
        List<Entry> entries = repository.findByActiveTrue().stream()
                .map(product -> entry(product, materials.get(product.materialCode())))
                .toList();
        Set<String> withProducts = entries.stream().map(entry -> entry.product().materialCode()).collect(Collectors.toSet());
        List<MaterialEntry> childless = materials.values().stream()
                .filter(material -> !withProducts.contains(material.materialCode()))
                .map(CatalogProductSearchService::materialEntry)
                .toList();
        LOGGER.info("Índice de produtos do catálogo carregado: {} produtos, {} materiais sem produto.",
                entries.size(), childless.size());
        return new Index(entries, childless, Instant.now());
    }

    static Entry entry(CatalogProduct product, CatalogMaterial material) {
        List<CatalogProduct.Sku> skus = product.skus() == null ? List.of() : product.skus();
        Set<String> codeSet = Stream.concat(Stream.of(product.productCode()),
                        skus.stream().flatMap(sku -> Stream.of(sku.skuCode(), sku.manufacturerSku(), sku.gtin())))
                .filter(Objects::nonNull).map(CatalogProductSearchService::norm).filter(code -> !code.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        String codes = String.join(" ", codeSet);
        String strong = norm(String.join(" ", nonNull(product.productCode(), product.name(), product.brand(),
                product.manufacturer(), product.model(), codes)));
        String details = skus.stream()
                .flatMap(sku -> Stream.concat(Stream.of(sku.presentation(), sku.commercialUnit()),
                        (sku.values() == null ? List.<CatalogProduct.SkuValue>of() : sku.values()).stream()
                                .map(value -> join(value.value(), value.unit()))))
                .filter(Objects::nonNull).collect(Collectors.joining(" "));
        List<String> hierarchy = material == null ? List.of() : Stream.of(material.materialName(), material.familyName(),
                material.segmentName()).map(CatalogProductSearchService::norm).toList();
        String haystack = strong + " " + norm(details + " " + (material == null ? product.materialCode() : material.materialCode())
                + " " + String.join(" ", hierarchy));
        return new Entry(product, material, norm(product.brand()), strong, haystack, hierarchy, codeSet,
                completeness(product));
    }

    static MaterialEntry materialEntry(CatalogMaterial material) {
        List<String> hierarchy = Stream.of(material.materialName(), material.familyName(), material.segmentName())
                .map(CatalogProductSearchService::norm).toList();
        return new MaterialEntry(material, norm(material.materialCode()) + " " + String.join(" ", hierarchy), hierarchy);
    }

    /** Same weights as a product: the material name plays the role of the product's own name. */
    static int materialRelevance(MaterialEntry entry, List<String> terms, String query) {
        if (terms.isEmpty()) return 0;
        int score = query.equals(norm(entry.material().materialCode())) ? 1000 : 0;
        for (String term : terms) {
            score += 1 + (entry.hierarchy().get(0).contains(term) ? 3 : 0);
            for (String level : entry.hierarchy()) if (level.contains(term)) score += 2;
        }
        return score;
    }

    static int completeness(CatalogProduct product) {
        List<CatalogProduct.Sku> skus = product.skus() == null ? List.of() : product.skus();
        String coverage = norm(product.documentalCoverage());
        int values = skus.stream().mapToInt(sku -> sku.values() == null ? 0 : sku.values().size()).max().orElse(0);
        return (!isBlank(product.imageUrl()) ? 1000 : 0)
                + (coverage.startsWith("completa") || coverage.startsWith("integral") ? 100 : 0)
                + Math.min(values, 5) * 10
                + (skus.stream().anyMatch(sku -> !isBlank(sku.gtin())) ? 5 : 0)
                + (skus.stream().anyMatch(sku -> !isBlank(sku.manufacturerSku())) ? 3 : 0)
                + (!isBlank(product.brand()) ? 2 : 0);
    }

    /**
     * Exact code (product, SKU, GTIN, manufacturer SKU) first. Then, per term: found in the product's own
     * name/brand/model/codes (+3) and in each level of its hierarchy (+2 for material, family, segment), so
     * "porcelanato" ranks porcelain tiles above a cutting disc that only mentions porcelain in its name.
     */
    static int relevance(Entry entry, List<String> terms, String query) {
        if (terms.isEmpty()) return 0;
        int score = 0;
        if (!query.isEmpty() && entry.codes().contains(query)) score += 1000;
        for (String term : terms) {
            score += 1 + (entry.strong().contains(term) ? 3 : 0);
            for (String level : entry.hierarchy()) if (level.contains(term)) score += 2;
        }
        return score;
    }

    static int compareCodes(String a, String b) {
        String[] left = a.split("\\."), right = b.split("\\.");
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            int cmp = comparePart(left[i], right[i]);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(left.length, right.length);
    }

    /** "P0010" vs "P9": same letter prefix, then numeric value of the trailing digits. */
    private static int comparePart(String a, String b) {
        int digitsA = trailingDigits(a), digitsB = trailingDigits(b);
        int cmp = a.substring(0, a.length() - digitsA).compareTo(b.substring(0, b.length() - digitsB));
        if (cmp != 0) return cmp;
        if (digitsA == 0 || digitsB == 0 || digitsA > 18 || digitsB > 18) return a.compareTo(b);
        return Long.compare(Long.parseLong(a.substring(a.length() - digitsA)), Long.parseLong(b.substring(b.length() - digitsB)));
    }

    private static int trailingDigits(String text) {
        int count = 0;
        while (count < text.length() && Character.isDigit(text.charAt(text.length() - 1 - count))) count++;
        return count;
    }

    static List<String> terms(String query) {
        String normalized = norm(query);
        return normalized.isEmpty() ? List.of() : List.of(normalized.split("\\s+"));
    }

    private static final java.util.regex.Pattern MARKS = java.util.regex.Pattern.compile("\\p{M}");
    private static final java.util.regex.Pattern SPACES = java.util.regex.Pattern.compile("\\s+");
    /** "60 x 60", "60×60" and "60X60" all become "60x60" so dimensions match however they are typed. */
    private static final java.util.regex.Pattern DIMENSION = java.util.regex.Pattern.compile("(\\d)\\s*[x×*]\\s*(?=\\d)");

    static String norm(String text) {
        if (text == null || text.isEmpty()) return "";
        String plain = MARKS.matcher(Normalizer.normalize(text.replace('×', 'x'), Normalizer.Form.NFD)).replaceAll("")
                .toLowerCase(Locale.ROOT);
        return SPACES.matcher(DIMENSION.matcher(plain).replaceAll("$1x")).replaceAll(" ").trim();
    }

    private static boolean sameOrEmpty(String filter, String value) {
        return isBlank(filter) || filter.trim().equals(value);
    }

    private static String[] nonNull(String... values) {
        return Stream.of(values).filter(Objects::nonNull).toArray(String[]::new);
    }

    private static String join(String value, String unit) {
        if (value == null) return null;
        return unit == null ? value : value + " " + unit;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }
}
