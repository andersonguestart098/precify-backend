package br.com.buscaproduto.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import br.com.buscaproduto.service.CatalogSyncService;

/**
 * Optional: with APP_CATALOG_SYNC_ON_STARTUP=true the bundled PRICO delivery is applied once, in
 * background, after the application is ready. A delivery already applied successfully (same file
 * hash) is not applied again.
 */
@Configuration
@ConditionalOnProperty(name = "app.catalog.sync-on-startup", havingValue = "true")
public class CatalogSyncStartupConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogSyncStartupConfig.class);
    private final CatalogSyncService service;

    public CatalogSyncStartupConfig(CatalogSyncService service) { this.service = service; }

    @EventListener(ApplicationReadyEvent.class)
    public void syncBundledCatalog() {
        try {
            if (service.alreadyApplied()) {
                LOGGER.info("Lote PRICO embarcado já aplicado; sincronização ignorada.");
                return;
            }
            var started = service.start();
            LOGGER.info("Sincronização do lote {} iniciada em background (id {}).", started.batchId(), started.id());
        } catch (RuntimeException exception) {
            LOGGER.error("Não foi possível iniciar a sincronização do catálogo no startup.", exception);
        }
    }
}
