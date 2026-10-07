package br.com.buscaproduto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import br.com.buscaproduto.dto.ProductSearchPage;
import br.com.buscaproduto.dto.ProductSearchRequest;
import br.com.buscaproduto.model.CatalogMaterial;
import br.com.buscaproduto.model.CatalogProduct;
import br.com.buscaproduto.repository.CatalogProductRepository;

class CatalogProductSearchServiceTest {
    final CatalogProductRepository repository = mock(CatalogProductRepository.class);
    final CatalogService catalog = mock(CatalogService.class);
    final CatalogProductSearchService service = new CatalogProductSearchService(repository, catalog);

    CatalogProductSearchServiceTest() {
        when(catalog.findAll()).thenReturn(List.of(
                material("1.1.1", "Areia fina natural", "1.1", "Areias", "1", "Agregados, Solos e Minerais"),
                material("1.1.2", "Areia média natural", "1.1", "Areias", "1", "Agregados, Solos e Minerais"),
                material("30.1.1", "Cabo de rede", "30.1", "Cabeamento", "30", "Telecomunicações")));
        when(repository.findByActiveTrue()).thenReturn(List.of(
                product("1.1.1.P0022", "1.1.1", "Areia fina natural de Jacareí", "JRCAMPEÃO", "PARCIAL",
                        sku("1.1.1.P0022.S0001", null, null, "saco de 20 kg", value("Formato de fornecimento", "Saco 20 kg", null))),
                product("1.1.2.P0008", "1.1.2", "Areia média", "MB Areias", "COMPLETA",
                        sku("1.1.2.P0008.S0001", null, null, "Saco 20 kg de areia média", value("Unidade", "saco", null),
                                value("Formato", "Saco 20 kg", null))),
                product("1.1.2.P0003", "1.1.2", "Areia média lavada", "MB Areias", "PARCIAL",
                        sku("1.1.2.P0003.S0001", null, null, "a granel")),
                product("30.1.1.P0005", "30.1.1", "SOHOPLUS U/UTP CAT.6", "Furukawa", "PARCIAL",
                        sku("30.1.1.P0005.S0001", "35123456", "7891234567895", "caixa 305 m", value("Categoria", "Cat 6", null)))));
    }

    @Test void filtersByHierarchyAndAllTermsIgnoringAccents() {
        var page = service.search(request("areia media", null, null, null, null), 0, 10, null);
        assertThat(codes(page)).containsExactly("1.1.2.P0008", "1.1.2.P0003");
        assertThat(codes(service.search(request("jacarei", null, null, null, null), 0, 10, null))).containsExactly("1.1.1.P0022");
        assertThat(codes(service.search(request(null, "1", null, null, null), 0, 10, null))).hasSize(3);
        assertThat(codes(service.search(request(null, null, "30.1", null, null), 0, 10, null))).containsExactly("30.1.1.P0005");
        assertThat(service.search(request("areia cabo", null, null, null, null), 0, 10, null).totalElements()).isZero();
    }

    @Test void findsBySkuCodesGtinAndMaterialNamesAndPutsExactCodeFirst() {
        assertThat(codes(service.search(request("7891234567895", null, null, null, null), 0, 10, null))).containsExactly("30.1.1.P0005");
        assertThat(codes(service.search(request("35123456", null, null, null, null), 0, 10, null))).containsExactly("30.1.1.P0005");
        assertThat(codes(service.search(request("telecomunicacoes", null, null, null, null), 0, 10, null))).containsExactly("30.1.1.P0005");
        assertThat(codes(service.search(request("1.1.2.p0003", null, null, null, null), 0, 10, null))).first().isEqualTo("1.1.2.P0003");
    }

    @Test void completeRecordsComeFirstWithoutQuery() {
        var page = service.search(request(null, null, null, "1.1.2", null), 0, 10, null);
        assertThat(codes(page)).containsExactly("1.1.2.P0008", "1.1.2.P0003");
        assertThat(page.content().get(0).materialName()).isEqualTo("Areia média natural");
        assertThat(page.content().get(0).familyName()).isEqualTo("Areias");
    }

    @Test void facetsIgnoreTheirOwnFilterSoOtherChoicesStayVisible() {
        var page = service.search(request(null, "1", null, null, "mb areias"), 0, 10, null);
        assertThat(codes(page)).containsExactly("1.1.2.P0008", "1.1.2.P0003");
        assertThat(page.brands()).extracting(ProductSearchPage.Facet::name).containsExactly("MB Areias", "JRCAMPEÃO");
        assertThat(page.materialCounts()).containsEntry("1.1.2", 2L).doesNotContainKey("1.1.1");
        var byMaterial = service.search(request(null, null, null, "1.1.1", null), 0, 10, null);
        assertThat(byMaterial.materialCounts()).containsEntry("1.1.1", 1L).containsEntry("1.1.2", 2L).containsEntry("30.1.1", 1L);
        assertThat(byMaterial.brands()).extracting(ProductSearchPage.Facet::name).containsExactly("JRCAMPEÃO");
    }

    @Test void paginatesAndRestrictsToFavorites() {
        var first = service.search(request(null, null, null, null, null), 0, 3, null);
        var second = service.search(request(null, null, null, null, null), 1, 3, null);
        assertThat(first.totalElements()).isEqualTo(4);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(codes(first)).hasSize(3);
        assertThat(codes(second)).hasSize(1).doesNotContainAnyElementsOf(codes(first));
        assertThat(codes(service.search(request(null, null, null, null, null), 0, 10, Set.of("30.1.1.P0005"))))
                .containsExactly("30.1.1.P0005");
        verify(repository, times(1)).findByActiveTrue();
        service.invalidate();
        service.search(request(null, null, null, null, null), 0, 10, null);
        verify(repository, times(2)).findByActiveTrue();
    }

    @Test void hierarchyMatchOutranksIncidentalMentionAndOnlyCodesGetExactBonus() {
        var repo = mock(CatalogProductRepository.class);
        var cat = mock(CatalogService.class);
        when(cat.findAll()).thenReturn(List.of(
                material("7.1.1", "Porcelanato técnico", "7.1", "Porcelanatos", "7", "Cerâmicos e Porcelanatos"),
                material("40.1.5", "Disco diamantado de corte", "40.1", "Discos", "40", "Ferramentas")));
        when(repo.findByActiveTrue()).thenReturn(List.of(
                product("40.1.5.P0003", "40.1.5", "Norton Pro Porcelanato", "Norton", "COMPLETA", sku("40.1.5.P0003.S0001", null, null, "1 un")),
                product("7.1.1.P8001", "7.1.1", "Mineral", "Portobello", "PARCIAL", sku("7.1.1.P8001.S0001", null, null, "caixa"))));
        var search = new CatalogProductSearchService(repo, cat);
        assertThat(codes(search.search(request("porcelanato", null, null, null, null), 0, 10, null)))
                .containsExactly("7.1.1.P8001", "40.1.5.P0003");
        assertThat(codes(search.search(request("40.1.5.P0003.S0001", null, null, null, null), 0, 10, null)))
                .containsExactly("40.1.5.P0003");
    }

    @Test void naturalCodeOrder() {
        assertThat(CatalogProductSearchService.compareCodes("1.1.10", "1.1.9")).isPositive();
        assertThat(CatalogProductSearchService.compareCodes("1.1.1.P0010", "1.1.1.P0009")).isPositive();
        assertThat(CatalogProductSearchService.compareCodes("1.1.1.P90001", "1.1.1.P2001")).isPositive();
    }

    private static List<String> codes(ProductSearchPage page) {
        return page.content().stream().map(result -> result.product().productCode()).toList();
    }

    private static ProductSearchRequest request(String query, String segment, String family, String material, String brand) {
        return new ProductSearchRequest(query, segment, family, material, brand, null);
    }

    private static CatalogMaterial material(String code, String name, String family, String familyName, String segment,
            String segmentName) {
        return new CatalogMaterial(code, segment, segmentName, family, familyName, name, "EM_REVISÃO", "", List.of());
    }

    private static CatalogProduct product(String code, String material, String name, String brand, String coverage,
            CatalogProduct.Sku... skus) {
        return new CatalogProduct(code, null, material, material.substring(0, material.lastIndexOf('.')),
                material.split("\\.")[0], name, brand, null, null, null, null, coverage, null, null, List.of(skus),
                "PRICO_TESTE", "hash", true, null, null, null, null);
    }

    private static CatalogProduct.Sku sku(String code, String manufacturerSku, String gtin, String presentation,
            CatalogProduct.SkuValue... values) {
        return new CatalogProduct.Sku(code, null, manufacturerSku, gtin, "un", null, presentation, null, null, null, null,
                null, null, List.of(values));
    }

    private static CatalogProduct.SkuValue value(String attribute, String value, String unit) {
        return new CatalogProduct.SkuValue("x.V01", attribute, value, null, null, unit, null, null, null, null);
    }
}
