package br.com.buscaproduto.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.buscaproduto.model.CatalogMaterial;
import br.com.buscaproduto.service.CatalogService;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final CatalogService service;

    public CatalogController(CatalogService service) { this.service = service; }

    @GetMapping
    public List<CatalogMaterial> findAll() { return service.findAll(); }

    @PostMapping("/import")
    public Map<String, Long> importCatalog() { return Map.of("materials", service.importCatalog()); }
}
