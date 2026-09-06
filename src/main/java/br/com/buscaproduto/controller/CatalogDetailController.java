package br.com.buscaproduto.controller;
import org.springframework.web.bind.annotation.*;
import br.com.buscaproduto.service.CatalogSearchService;
@RestController
@RequestMapping("/api/catalog")
public class CatalogDetailController {
    private final CatalogSearchService service;
    public CatalogDetailController(CatalogSearchService service) { this.service = service; }
    @GetMapping("/{code}/details")
    public CatalogSearchService.Detail detail(@PathVariable String code) { return service.detail(code); }
}
