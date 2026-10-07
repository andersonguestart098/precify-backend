package br.com.buscaproduto.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;

import br.com.buscaproduto.model.CatalogImport;
import br.com.buscaproduto.model.CatalogMaterial;
import br.com.buscaproduto.model.CatalogProduct;
import br.com.buscaproduto.repository.CatalogImportRepository;
import br.com.buscaproduto.repository.CatalogMaterialRepository;

/**
 * Applies the bundled PRICO delivery to the database in two steps:
 * <ol>
 * <li>materials: upsert of the catalog v2 (new materials, renamed names, contract fields), keeping
 * image URLs; materials that exist only in the database are left untouched;</li>
 * <li>products: upsert of the documented products with their SKUs; unchanged products are skipped
 * (content hash) and products missing from the delivery are marked {@code active=false}.</li>
 * </ol>
 * Everything is validated before the first write. Nothing is deleted.
 */
@Service
public class CatalogSyncService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogSyncService.class);
    private static final int BULK_SIZE = 500;
    private static final int MAX_ERRORS = 200;

    private final CatalogService catalogService;
    private final CatalogMaterialRepository materials;
    private final CatalogImportRepository imports;
    private final MongoTemplate mongo;
    private final ObjectMapper objectMapper;
    private final CatalogProductSearchService productSearch;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "catalog-sync");
        thread.setDaemon(true);
        return thread;
    });

    public CatalogSyncService(CatalogService catalogService, CatalogMaterialRepository materials,
            CatalogImportRepository imports, MongoTemplate mongo, ObjectMapper objectMapper,
            CatalogProductSearchService productSearch) {
        this.catalogService = catalogService;
        this.materials = materials;
        this.imports = imports;
        this.mongo = mongo;
        this.objectMapper = objectMapper;
        this.productSearch = productSearch;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public record Plan(CatalogProductBundle.Manifest manifest, boolean alreadyApplied, CatalogImport.Report report,
            List<String> errors) {
    }

    /** Dry run: what a sync would do right now. Reads only. */
    public Plan plan() {
        var bundle = CatalogProductBundle.read(objectMapper);
        var catalog = catalogService.readBundledCatalog();
        var existing = existingMaterials();
        var materialChanges = diffMaterials(catalog, existing);
        var productChanges = diffProducts(bundle, existingProductState());
        var errors = validate(merge(catalog, existing), bundle);
        return new Plan(bundle.manifest(), alreadyApplied(bundle),
                report(catalog, materialChanges, bundle, productChanges), errors);
    }

    public boolean alreadyApplied() {
        return alreadyApplied(CatalogProductBundle.read(objectMapper));
    }

    public List<CatalogImport> history() {
        return imports.findTop20ByOrderByStartedAtDesc();
    }

    /** Starts the sync in background (HTTP and Heroku boot must not wait for thousands of upserts). */
    public CatalogImport start() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Já existe uma sincronização do catálogo em andamento.");
        }
        try {
            var bundle = CatalogProductBundle.read(objectMapper);
            var manifest = bundle.manifest();
            var record = imports.save(new CatalogImport(UUID.randomUUID().toString(), manifest.batchId(),
                    manifest.sourceFile(), manifest.sourceSha256(), bundle.productsSha256(), CatalogImport.RUNNING,
                    Instant.now(), null, null, List.of()));
            executor.submit(() -> {
                try {
                    run(record, bundle);
                } finally {
                    running.set(false);
                }
            });
            return record;
        } catch (RuntimeException exception) {
            running.set(false);
            throw exception;
        }
    }

    void run(CatalogImport record, CatalogProductBundle bundle) {
        try {
            var catalog = catalogService.readBundledCatalog();
            var existing = existingMaterials();
            var errors = validate(merge(catalog, existing), bundle);
            if (!errors.isEmpty()) {
                finish(record, CatalogImport.FAILED, null, errors);
                LOGGER.error("Sincronização do catálogo {} abortada antes de gravar: {} erros de validação.",
                        record.batchId(), errors.size());
                return;
            }
            var materialChanges = diffMaterials(catalog, existing);
            writeMaterials(materialChanges);
            var productChanges = diffProducts(bundle, existingProductState());
            writeProducts(bundle, productChanges);
            var report = report(catalog, materialChanges, bundle, productChanges);
            productSearch.invalidate();
            finish(record, CatalogImport.SUCCESS, report, List.of());
            LOGGER.info("Catálogo {} sincronizado: {}", record.batchId(), report);
        } catch (RuntimeException exception) {
            LOGGER.error("Falha ao sincronizar o catálogo {}", record.batchId(), exception);
            finish(record, CatalogImport.FAILED, null, List.of(String.valueOf(exception.getMessage())));
        }
    }

    private boolean alreadyApplied(CatalogProductBundle bundle) {
        return imports.existsByProductsSha256AndStatus(bundle.productsSha256(), CatalogImport.SUCCESS);
    }

    private void finish(CatalogImport record, String status, CatalogImport.Report report, List<String> errors) {
        imports.save(new CatalogImport(record.id(), record.batchId(), record.sourceFile(), record.sourceSha256(),
                record.productsSha256(), status, record.startedAt(), Instant.now(), report, errors));
    }

    // ------------------------------------------------------------------------------------------
    // Materials
    // ------------------------------------------------------------------------------------------

    record MaterialChanges(List<CatalogMaterial> inserted, List<CatalogMaterial> updated, int unchanged,
            int onlyInDatabase) {
    }

    static MaterialChanges diffMaterials(List<CatalogMaterial> bundle, Map<String, CatalogMaterial> existing) {
        List<CatalogMaterial> inserted = new ArrayList<>();
        List<CatalogMaterial> updated = new ArrayList<>();
        int unchanged = 0;
        Set<String> bundleCodes = new HashSet<>();
        for (CatalogMaterial material : bundle) {
            bundleCodes.add(material.materialCode());
            CatalogMaterial current = existing.get(material.materialCode());
            if (current == null) {
                inserted.add(material);
                continue;
            }
            CatalogMaterial merged = material.withImages(
                    Objects.requireNonNullElse(current.imageUrl(), ""),
                    Objects.requireNonNullElse(current.supplierLogoUrl(), ""));
            if (merged.equals(current)) unchanged++;
            else updated.add(merged);
        }
        int onlyInDatabase = (int) existing.keySet().stream().filter(code -> !bundleCodes.contains(code)).count();
        return new MaterialChanges(List.copyOf(inserted), List.copyOf(updated), unchanged, onlyInDatabase);
    }

    private Map<String, CatalogMaterial> existingMaterials() {
        return materials.findAll().stream()
                .collect(Collectors.toMap(CatalogMaterial::materialCode, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    /** Catalog as it will be after the material step: bundle plus materials kept only in the database. */
    private static List<CatalogMaterial> merge(List<CatalogMaterial> bundle, Map<String, CatalogMaterial> existing) {
        Map<String, CatalogMaterial> merged = new LinkedHashMap<>(existing);
        bundle.forEach(material -> merged.put(material.materialCode(), material));
        return List.copyOf(merged.values());
    }

    private void writeMaterials(MaterialChanges changes) {
        List<CatalogMaterial> changed = new ArrayList<>(changes.inserted());
        changed.addAll(changes.updated());
        for (int from = 0; from < changed.size(); from += BULK_SIZE) {
            BulkOperations bulk = mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, CatalogMaterial.class);
            for (CatalogMaterial material : changed.subList(from, Math.min(from + BULK_SIZE, changed.size()))) {
                bulk.replaceOne(Query.query(Criteria.where("_id").is(material.materialCode())), material,
                        FindAndReplaceOptions.options().upsert());
            }
            bulk.execute();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Products
    // ------------------------------------------------------------------------------------------

    record ProductState(String contentHash, Boolean active, String imageUrl, Instant createdAt) {
    }

    record ProductChanges(List<String> inserted, List<String> updated, int unchanged, List<String> withdrawn) {
    }

    static ProductChanges diffProducts(CatalogProductBundle bundle, Map<String, ProductState> existing) {
        List<String> inserted = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        int unchanged = 0;
        Set<String> bundleCodes = new HashSet<>();
        for (var entry : bundle.products()) {
            String code = entry.product().productCode();
            bundleCodes.add(code);
            ProductState state = existing.get(code);
            if (state == null) inserted.add(code);
            else if (!entry.contentHash().equals(state.contentHash()) || !Boolean.TRUE.equals(state.active())) updated.add(code);
            else unchanged++;
        }
        List<String> withdrawn = existing.entrySet().stream()
                .filter(e -> Boolean.TRUE.equals(e.getValue().active()) && !bundleCodes.contains(e.getKey()))
                .map(Map.Entry::getKey).sorted().toList();
        return new ProductChanges(List.copyOf(inserted), List.copyOf(updated), unchanged, withdrawn);
    }

    private Map<String, ProductState> existingProductState() {
        Query query = new Query();
        query.fields().include("_id", "contentHash", "active", "imageUrl", "createdAt");
        Map<String, ProductState> state = new HashMap<>();
        for (Document document : mongo.find(query, Document.class, "catalog_products")) {
            var created = document.getDate("createdAt");
            state.put(document.getString("_id"), new ProductState(document.getString("contentHash"),
                    document.getBoolean("active"), document.getString("imageUrl"),
                    created == null ? null : created.toInstant()));
        }
        return state;
    }

    private void writeProducts(CatalogProductBundle bundle, ProductChanges changes) {
        Instant now = Instant.now();
        Set<String> toWrite = new HashSet<>(changes.inserted());
        toWrite.addAll(changes.updated());
        Map<String, ProductState> state = toWrite.isEmpty() ? Map.of() : existingProductState();
        List<CatalogProduct> documents = new ArrayList<>();
        for (var entry : bundle.products()) {
            CatalogProduct product = entry.product();
            if (!toWrite.contains(product.productCode())) continue;
            ProductState current = state.get(product.productCode());
            documents.add(product.withSyncState(entry.contentHash(), true,
                    current == null ? null : current.imageUrl(),
                    current == null || current.createdAt() == null ? now : current.createdAt(), now));
        }
        for (int from = 0; from < documents.size(); from += BULK_SIZE) {
            BulkOperations bulk = mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, CatalogProduct.class);
            for (CatalogProduct product : documents.subList(from, Math.min(from + BULK_SIZE, documents.size()))) {
                bulk.replaceOne(Query.query(Criteria.where("_id").is(product.productCode())), product,
                        FindAndReplaceOptions.options().upsert());
            }
            bulk.execute();
        }
        if (!changes.withdrawn().isEmpty()) {
            mongo.updateMulti(Query.query(Criteria.where("_id").in(changes.withdrawn())),
                    new Update().set("active", false).set("withdrawnAt", now), CatalogProduct.class);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Validation and report
    // ------------------------------------------------------------------------------------------

    static List<String> validate(List<CatalogMaterial> catalog, CatalogProductBundle bundle) {
        List<String> errors = new ArrayList<>();
        Map<String, CatalogMaterial> byCode = catalog.stream()
                .collect(Collectors.toMap(CatalogMaterial::materialCode, Function.identity(), (a, b) -> a));
        var manifest = bundle.manifest();
        if (manifest == null || manifest.batchId() == null) errors.add("Manifesto do lote ausente ou sem batchId.");
        else {
            if (!Objects.equals(manifest.productsSha256(), bundle.productsSha256()))
                errors.add("Hash do arquivo de produtos difere do manifesto (arquivo alterado após a consolidação).");
            Map<String, Integer> counts = manifest.counts() == null ? Map.of() : manifest.counts();
            if (!Objects.equals(counts.get("products"), bundle.products().size()))
                errors.add("Manifesto declara %s produtos; arquivo tem %d.".formatted(counts.get("products"), bundle.products().size()));
            if (!Objects.equals(counts.get("skus"), bundle.skuCount()))
                errors.add("Manifesto declara %s SKUs; arquivo tem %d.".formatted(counts.get("skus"), bundle.skuCount()));
        }
        Set<String> products = new HashSet<>();
        Set<String> skus = new HashSet<>();
        for (var entry : bundle.products()) {
            if (errors.size() >= MAX_ERRORS) break;
            CatalogProduct product = entry.product();
            String code = product.productCode();
            if (code == null || !products.add(code)) { errors.add("Produto sem código ou duplicado: " + code); continue; }
            CatalogMaterial material = byCode.get(product.materialCode());
            if (material == null) { errors.add(code + ": material inexistente " + product.materialCode()); continue; }
            if (!code.startsWith(material.materialCode() + ".P")) errors.add(code + ": código não pertence ao material " + material.materialCode());
            if (!Objects.equals(product.familyCode(), material.familyCode()) || !Objects.equals(product.segmentCode(), material.segmentCode()))
                errors.add(code + ": família/segmento divergem do material " + material.materialCode());
            if (product.name() == null || product.name().isBlank()) errors.add(code + ": produto sem nome");
            if (product.skus() == null || product.skus().isEmpty()) { errors.add(code + ": produto sem SKU"); continue; }
            Map<String, CatalogMaterial.CatalogVariation> variations = material.variations().stream()
                    .collect(Collectors.toMap(CatalogMaterial.CatalogVariation::variationCode, Function.identity(), (a, b) -> a));
            for (var sku : product.skus()) {
                if (sku.skuCode() == null || !sku.skuCode().startsWith(code + ".S")) errors.add(code + ": SKU fora do produto " + sku.skuCode());
                if (!skus.add(String.valueOf(sku.skuCode()))) errors.add("SKU duplicado: " + sku.skuCode());
                for (var value : sku.values() == null ? List.<CatalogProduct.SkuValue>of() : sku.values()) {
                    var variation = variations.get(value.variationCode());
                    if (variation == null) {
                        errors.add(sku.skuCode() + ": variação " + value.variationCode() + " não pertence ao material");
                    } else if (value.optionCode() != null && variation.options().stream()
                            .noneMatch(option -> option.optionCode().equals(value.optionCode()))) {
                        errors.add(sku.skuCode() + ": opção " + value.optionCode() + " não pertence à variação");
                    }
                }
            }
        }
        return errors.size() > MAX_ERRORS ? List.copyOf(errors.subList(0, MAX_ERRORS)) : List.copyOf(errors);
    }

    private static CatalogImport.Report report(List<CatalogMaterial> catalog, MaterialChanges materials,
            CatalogProductBundle bundle, ProductChanges products) {
        return new CatalogImport.Report(catalog.size(), materials.inserted().size(), materials.updated().size(),
                materials.unchanged(), materials.onlyInDatabase(),
                bundle.products().size(), products.inserted().size(), products.updated().size(), products.unchanged(),
                products.withdrawn().size(), bundle.skuCount());
    }
}
