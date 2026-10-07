package br.com.buscaproduto.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.com.buscaproduto.model.CatalogImport;
import br.com.buscaproduto.service.CatalogSyncService;

/** ADMIN only (see SecurityConfig): applies the bundled PRICO delivery to the database. */
@RestController
@RequestMapping("/api/admin/catalog/sync")
public class CatalogAdminController {
    private final CatalogSyncService service;

    public CatalogAdminController(CatalogSyncService service) { this.service = service; }

    /** {@code dryRun=true} returns the plan and validation errors without writing anything. */
    @PostMapping
    public ResponseEntity<?> sync(@RequestParam(defaultValue = "false") boolean dryRun) {
        if (dryRun) return ResponseEntity.ok(service.plan());
        try {
            return ResponseEntity.accepted().body(service.start());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    @GetMapping
    public List<CatalogImport> history() { return service.history(); }
}
