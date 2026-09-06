package br.com.buscaproduto.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;

import br.com.buscaproduto.dto.RankedProduct;
import br.com.buscaproduto.dto.SearchPageResponse;
import br.com.buscaproduto.dto.SearchRequest;
import br.com.buscaproduto.dto.TechnicalCriterion;
import br.com.buscaproduto.model.Product;
import br.com.buscaproduto.repository.ProductRepository;

@Service
public class SearchService {
    private final ProductRepository productRepository;
    private final CompatibilityService compatibilityService;

    public SearchService(ProductRepository productRepository, CompatibilityService compatibilityService) {
        this.productRepository = productRepository;
        this.compatibilityService = compatibilityService;
    }

    public List<RankedProduct> search(SearchRequest request) {
        List<TechnicalCriterion> technicalCriteria = request.criteria().stream()
                .filter(criterion -> !isCatalogFilter(criterion.key())).toList();
        List<Product> candidates = productRepository.findAll().stream()
                .filter(matchesFamily(request.familyCode()))
                .filter(matchesCatalogFilters(request.criteria())).toList();
        SearchRequest rankingRequest = new SearchRequest(request.familyCode(), request.query(), technicalCriteria,
                request.includeAlternatives());
        return compatibilityService.rank(candidates, rankingRequest);
    }

    public SearchPageResponse search(SearchRequest request, int page, int size) {
        return SearchPageResponse.from(search(request), page, size);
    }

    private Predicate<Product> matchesFamily(String familyCode) {
        if (isBlank(familyCode)) return product -> true;
        return product -> equalsIgnoreCase(product.category(), familyCode);
    }

    private Predicate<Product> matchesCatalogFilters(List<TechnicalCriterion> criteria) {
        return product -> criteria.stream().allMatch(criterion -> switch (criterion.key()) {
            case "segment" -> isBlank(criterion.value()) || equalsIgnoreCase(product.segment(), criterion.value());
            case "material" -> isBlank(criterion.value()) || equalsIgnoreCase(product.material(), criterion.value());
            case "variation" -> isBlank(criterion.value()) || product.variations().stream()
                    .anyMatch(variation -> equalsIgnoreCase(variation.label(), criterion.value()));
            case "state" -> isBlank(criterion.value()) || product.variations().stream()
                    .anyMatch(variation -> equalsIgnoreCase(variation.quote().region(), criterion.value()));
            case "price" -> isBlank(criterion.value()) || product.variations().stream()
                    .anyMatch(variation -> matchesPriceBand(variation.quote().value(), criterion.value()));
            default -> true;
        });
    }

    private boolean isCatalogFilter(String key) {
        return switch (key) {
            case "segment", "material", "variation", "state", "price" -> true;
            default -> false;
        };
    }

    private boolean matchesPriceBand(BigDecimal price, String band) {
        String normalized = band.toLowerCase(Locale.ROOT).replace("r$", "").replace(" ", "");
        double value = price.doubleValue();
        if (normalized.startsWith("até")) return value <= 100;
        if (normalized.contains("100a150")) return value >= 100 && value <= 150;
        if (normalized.contains("150a200")) return value >= 150 && value <= 200;
        if (normalized.startsWith("acima")) return value > 200;
        if (band.matches("[0-9]+([.,][0-9]+)?")) return price.compareTo(new BigDecimal(band.replace(',', '.'))) == 0;
        return true;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }
}
