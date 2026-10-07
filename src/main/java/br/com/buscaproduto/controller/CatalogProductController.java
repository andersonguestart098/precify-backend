package br.com.buscaproduto.controller;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.com.buscaproduto.dto.ProductSearchPage;
import br.com.buscaproduto.dto.ProductSearchRequest;
import br.com.buscaproduto.favorite.Favorite;
import br.com.buscaproduto.favorite.FavoriteRepository;
import br.com.buscaproduto.model.CatalogProduct;
import br.com.buscaproduto.repository.CatalogProductRepository;
import br.com.buscaproduto.service.CatalogProductSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Read access to catalog products (level 4) and their SKUs (level 5). */
@Validated
@RestController
@RequestMapping("/api/catalog")
public class CatalogProductController {
    private final CatalogProductRepository repository;
    private final CatalogProductSearchService search;
    private final FavoriteRepository favorites;

    public CatalogProductController(CatalogProductRepository repository, CatalogProductSearchService search,
            FavoriteRepository favorites) {
        this.repository = repository;
        this.search = search;
        this.favorites = favorites;
    }

    /** Main listing of the search screen: products filtered by segment, family, material, brand and text. */
    @PostMapping("/products/search")
    public ProductSearchPage search(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProductSearchRequest request,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        Set<String> allowed = null;
        if (Boolean.TRUE.equals(request.onlyFavorites())) {
            if (jwt == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            allowed = favorites.findByUserIdAndEntityType(jwt.getSubject(), "PRODUCT").stream()
                    .map(Favorite::entityId).collect(Collectors.toSet());
        }
        return search.search(request, page, size, allowed);
    }

    @GetMapping("/{materialCode}/products")
    public List<CatalogProduct> byMaterial(@PathVariable String materialCode) {
        return repository.findByMaterialCodeAndActiveTrueOrderByProductCodeAsc(materialCode);
    }

    @GetMapping("/products/{productCode}")
    public CatalogProduct product(@PathVariable String productCode) {
        return repository.findById(productCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado."));
    }

    /** Exactly one of: {@code code} (SKU PRICO), {@code gtin} or {@code manufacturerSku}. */
    @GetMapping("/skus")
    public List<CatalogProduct> skus(@RequestParam(required = false) String code,
            @RequestParam(required = false) String gtin,
            @RequestParam(required = false) String manufacturerSku) {
        long informed = Stream.of(code, gtin, manufacturerSku).filter(v -> v != null && !v.isBlank()).count();
        if (informed != 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe exatamente um filtro: code, gtin ou manufacturerSku.");
        if (code != null && !code.isBlank()) return repository.findBySkus_SkuCode(code.trim());
        if (gtin != null && !gtin.isBlank()) return repository.findBySkus_Gtin(gtin.trim());
        return repository.findBySkus_ManufacturerSku(manufacturerSku.trim());
    }
}
