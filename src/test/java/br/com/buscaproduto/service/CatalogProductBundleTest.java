package br.com.buscaproduto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.buscaproduto.model.CatalogMaterial;
import br.com.buscaproduto.model.CatalogProduct;
import br.com.buscaproduto.repository.CatalogMaterialRepository;

class CatalogProductBundleTest {
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test void bundledProductsMatchManifestAndBundledCatalog() {
        var bundle = CatalogProductBundle.read(mapper);
        var catalog = new CatalogService(mock(CatalogMaterialRepository.class), mapper).readBundledCatalog();
        assertThat(bundle.manifest().batchId()).startsWith("PRICO_");
        assertThat(bundle.productsSha256()).isEqualTo(bundle.manifest().productsSha256());
        assertThat(bundle.products()).hasSize(9237);
        assertThat(bundle.skuCount()).isEqualTo(10513);
        assertThat(CatalogSyncService.validate(catalog, bundle)).isEmpty();
        assertThat(bundle.products()).allSatisfy(entry -> {
            var product = entry.product();
            assertThat(product.productCode()).matches(Pattern.quote(product.materialCode()) + "\\.P\\d{4,5}");
            assertThat(product.skus()).isNotEmpty().allSatisfy(sku ->
                assertThat(sku.skuCode()).matches(Pattern.quote(product.productCode()) + "\\.S\\d{4}"));
        });
    }

    @Test void materialSyncKeepsImagesAndOnlyRewritesWhatChanged() {
        var fromBundle = material("1.1.1", "Areia fina natural");
        var stored = material("1.1.1", "Areia fina").withImages("/api/media/0123456789abcdef01234567", "");
        var changes = CatalogSyncService.diffMaterials(List.of(fromBundle, material("6.7.1", "Tijolo de solo-cimento")),
                Map.of("1.1.1", stored, "7.2.1", material("7.2.1", "Azulejo cerâmico")));
        assertThat(changes.inserted()).extracting(CatalogMaterial::materialCode).containsExactly("6.7.1");
        assertThat(changes.updated()).singleElement().satisfies(updated -> {
            assertThat(updated.materialName()).isEqualTo("Areia fina natural");
            assertThat(updated.imageUrl()).isEqualTo("/api/media/0123456789abcdef01234567");
        });
        assertThat(changes.onlyInDatabase()).isEqualTo(1);
        var again = CatalogSyncService.diffMaterials(List.of(fromBundle), Map.of("1.1.1", changes.updated().get(0)));
        assertThat(again.unchanged()).isEqualTo(1);
        assertThat(again.updated()).isEmpty();
    }

    @Test void productSyncSkipsUnchangedAndWithdrawsMissingProducts() {
        var bundle = CatalogProductBundle.read(mapper);
        var same = bundle.products().get(0);
        var changed = bundle.products().get(1);
        var changes = CatalogSyncService.diffProducts(bundle, Map.of(
                same.product().productCode(), new CatalogSyncService.ProductState(same.contentHash(), true, "/api/media/x", Instant.EPOCH),
                changed.product().productCode(), new CatalogSyncService.ProductState("hash-anterior", true, null, Instant.EPOCH),
                "1.1.1.P9999", new CatalogSyncService.ProductState("h", true, null, Instant.EPOCH)));
        assertThat(changes.unchanged()).isEqualTo(1);
        assertThat(changes.updated()).containsExactly(changed.product().productCode());
        assertThat(changes.inserted()).hasSize(bundle.products().size() - 2);
        assertThat(changes.withdrawn()).containsExactly("1.1.1.P9999");
    }

    @Test void validationBlocksOrphansForeignVariationsAndDuplicatedSkus() {
        var catalog = List.of(new CatalogMaterial("1.1.1", "1", "Agregados", "1.1", "Areias", "Areia fina natural",
                "EM_REVISÃO", "", List.of(new CatalogMaterial.CatalogVariation("1.1.1.V01", "Unidade", "UNIDADE", "SIM", 1,
                        List.of(new CatalogMaterial.CatalogOption("1.1.1.V01.001", "m³", "m³", 1, "EM_REVISÃO")))))));
        var ok = product("1.1.1.P0001", "1.1.1", sku("1.1.1.P0001.S0001", value("1.1.1.V01", "1.1.1.V01.001")));
        var orphan = product("9.9.9.P0001", "9.9.9", sku("9.9.9.P0001.S0001"));
        var foreign = product("1.1.1.P0002", "1.1.1", sku("1.1.1.P0002.S0001", value("2.1.1.V01", null)));
        var badOption = product("1.1.1.P0003", "1.1.1", sku("1.1.1.P0003.S0001", value("1.1.1.V01", "1.1.1.V01.999")));
        var duplicated = product("1.1.1.P0004", "1.1.1", sku("1.1.1.P0001.S0001"));
        var bundle = bundle(ok, orphan, foreign, badOption, duplicated);
        assertThat(CatalogSyncService.validate(catalog, bundle))
                .anyMatch(e -> e.contains("material inexistente 9.9.9"))
                .anyMatch(e -> e.contains("variação 2.1.1.V01"))
                .anyMatch(e -> e.contains("opção 1.1.1.V01.999"))
                .anyMatch(e -> e.contains("SKU fora do produto"))
                .anyMatch(e -> e.contains("SKU duplicado"));
        assertThat(CatalogSyncService.validate(catalog, bundle(ok))).isEmpty();
    }

    private static CatalogMaterial material(String code, String name) {
        return new CatalogMaterial(code, code.split("\\.")[0], "Segmento", code.substring(0, code.lastIndexOf('.')),
                "Família", name, "EM_REVISÃO", "", List.of());
    }

    private static CatalogProductBundle bundle(CatalogProduct... products) {
        int skus = 0;
        for (var p : products) skus += p.skus().size();
        var manifest = new CatalogProductBundle.Manifest("PRICO_TESTE", "teste.xlsx", "x", "2026-10-06", "sha", null, null,
                Map.of("products", products.length, "skus", skus));
        return new CatalogProductBundle(manifest, "sha",
                java.util.Arrays.stream(products).map(p -> new CatalogProductBundle.Entry(p, p.productCode())).toList());
    }

    private static CatalogProduct product(String code, String material, CatalogProduct.Sku... skus) {
        return new CatalogProduct(code, null, material, material.substring(0, material.lastIndexOf('.')),
                material.split("\\.")[0], "Produto " + code, "Marca", null, null, null, null, "PARCIAL", null, null,
                List.of(skus), "PRICO_TESTE", null, null, null, null, null, null);
    }

    private static CatalogProduct.Sku sku(String code, CatalogProduct.SkuValue... values) {
        return new CatalogProduct.Sku(code, null, null, null, "un", null, "1 un", null, null, null, null, null, null,
                List.of(values));
    }

    private static CatalogProduct.SkuValue value(String variation, String option) {
        return new CatalogProduct.SkuValue(variation, "Atributo", "m³", null, null, null, option, null, null, null);
    }
}
