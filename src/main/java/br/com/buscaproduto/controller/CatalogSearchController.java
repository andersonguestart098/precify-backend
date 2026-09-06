package br.com.buscaproduto.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import br.com.buscaproduto.dto.*;
import br.com.buscaproduto.service.CatalogSearchService;

@Validated
@RestController
@RequestMapping("/api/catalog/search")
public class CatalogSearchController {
    private final CatalogSearchService service;
    public CatalogSearchController(CatalogSearchService service) { this.service = service; }
    @PostMapping
    public CatalogSearchPage search(@Valid @RequestBody SearchRequest request,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return service.search(request, page, size);
    }
}
