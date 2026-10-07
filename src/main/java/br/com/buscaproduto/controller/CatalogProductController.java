package br.com.buscaproduto.controller;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.com.buscaproduto.model.CatalogProduct;
import br.com.buscaproduto.repository.CatalogProductRepository;

/** Read access to catalog products (level 4) and their SKUs (level 5). */
@RestController
@RequestMapping("/api/catalog")
public class CatalogProductController {
    private final CatalogProductRepository repository;

    public CatalogProductController(CatalogProductRepository repository) { this.repository = repository; }

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
