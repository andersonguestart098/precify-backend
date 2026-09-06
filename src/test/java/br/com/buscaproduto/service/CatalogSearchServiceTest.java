package br.com.buscaproduto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import br.com.buscaproduto.model.*;
import br.com.buscaproduto.dto.*;
import br.com.buscaproduto.enums.*;
import br.com.buscaproduto.repository.ProductRepository;

class CatalogSearchServiceTest {
    final CatalogService catalog = mock(CatalogService.class);
    final ProductRepository products = mock(ProductRepository.class);
    final CatalogSearchService service = new CatalogSearchService(catalog, products);
    final CatalogMaterial material = new CatalogMaterial("1.1.1", "1", "Agregados", "1.1", "Areias",
            "Areia fina natural", "EM_REVISÃO", "Material canônico", List.of(
                new CatalogMaterial.CatalogVariation("1.1.1.V01", "Unidade", "UNIDADE", "SIM", 1,
                    List.of(new CatalogMaterial.CatalogOption("1.1.1.V01.001", "m³", "m³", 1, "EM_REVISÃO")))));
    CatalogSearchServiceTest() {
        when(catalog.findAll()).thenReturn(List.of(material));
        when(products.findAll()).thenReturn(List.of());
    }
    SearchRequest request(TechnicalCriterion... criteria) {
        return new SearchRequest("", "", List.of(criteria), false);
    }
    TechnicalCriterion filter(String key, String value) {
        return new TechnicalCriterion(key, key, value, CriterionMode.REQUIRED, CriterionOperator.EXACT, 1);
    }
    Product.Variation quote(String state, String value, String option) {
        return new Product.Variation("m³", new Product.Quote(new BigDecimal(value), "Fornecedor",
                LocalDate.of(2026, 9, 6), state), "1.1.1.V01", option);
    }
    Product product(List<Product.Variation> quotes, String code) {
        return new Product("p1", "Areia comercial", "Marca", "Modelo", "Outro", "Outro", "Outro",
                "Descrição", null, null, Map.of("unidade", "m³"), quotes, null, null, code, "1.1", "1");
    }
    @Test void showsCatalogWithoutCreatingFakeProducts() {
        var page = service.search(request(), 0, 10);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().getFirst().offers()).isEmpty();
        verify(products, never()).save(any());
    }
    @Test void linksProductByCanonicalMaterialCode() {
        when(products.findAll()).thenReturn(List.of(product(List.of(quote("RS", "92", "1.1.1.V01.001")), "1.1.1")));
        assertThat(service.search(request(), 0, 10).content().getFirst().offers()).hasSize(1);
    }
    @Test void doesNotMixPriceAndStateAcrossDifferentOffers() {
        when(products.findAll()).thenReturn(List.of(product(List.of(quote("RS", "180", null),
                quote("SP", "90", null)), "1.1.1")));
        assertThat(service.search(request(filter("state", "RS"), filter("price", "Até R$ 100")), 0, 10).content()).isEmpty();
    }
    @Test void optionWithoutOfferStillShowsMaterialButNoFakeQuote() {
        var page = service.search(request(filter("optionCode", "1.1.1.V01.001")), 0, 10);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().offers()).isEmpty();
        assertThat(service.search(request(filter("optionCode", "invalid")), 0, 10).content()).isEmpty();
    }
    @Test void legacyUnmappedAndZeroPlaceholderPricesDoNotBecomeOffers() {
        when(products.findAll()).thenReturn(List.of(product(List.of(quote("RS", "90", null)), null),
                product(List.of(quote("RS", "0", null)), "1.1.1")));
        assertThat(service.search(request(), 0, 10).content().getFirst().offers()).isEmpty();
    }
    @Test void searchesAccentsAndHandlesOutOfRangePage() {
        var request = new SearchRequest("", "areia fina", List.of(), false);
        assertThat(service.search(request, 0, 1).totalElements()).isEqualTo(1);
        assertThat(service.search(request, Integer.MAX_VALUE, 100).content()).isEmpty();
        assertThat(service.search(new SearchRequest("99", "", List.of(), false), 0, 10).content()).isEmpty();
    }

    @Test void prioritizesOffersBeforeCodeAndAppliesFavoritesBeforePagination() {
        var other = new CatalogMaterial("1.1.2", "1", "Agregados", "1.1", "Areias",
                "Areia média", "EM_REVISÃO", "", material.variations());
        when(catalog.findAll()).thenReturn(List.of(material, other));
        when(products.findAll()).thenReturn(List.of(product(List.of(quote("RS", "92", null)), "1.1.2")));
        assertThat(service.search(request(), 0, 1).content().getFirst().material().materialCode()).isEqualTo("1.1.2");
        var onlyFavorite = service.search(request(), 0, 1, java.util.Set.of("1.1.1"));
        assertThat(onlyFavorite.totalElements()).isEqualTo(1);
        assertThat(onlyFavorite.content().getFirst().material().materialCode()).isEqualTo("1.1.1");
    }

    @Test void photoAndVariationsRankAheadOfQuoteOnlyBeforePagination() {
        var complete = new CatalogMaterial("1.1.9", "1", "Agregados", "1.1", "Areias",
                "Areia com foto", "EM_REVISÃO", "", material.variations());
        when(catalog.findAll()).thenReturn(List.of(material, complete));
        var pictured = new Product("photo", "Areia com foto", "", "", "", "", "", "",
            "https://res.cloudinary.com/demo/image/upload/photo.jpg",
            "https://res.cloudinary.com/demo/image/upload/logo.jpg",
            Map.of(), List.of(), null, null, "1.1.9", "1.1", "1");
        when(products.findAll()).thenReturn(List.of(
            product(List.of(quote("RS", "92", null)), "1.1.1"), pictured));
        var first = service.search(request(), 0, 1).content().getFirst();
        assertThat(first.material().materialCode()).isEqualTo("1.1.9");
        assertThat(first.imageUrl()).isEqualTo(pictured.imageUrl());
        assertThat(first.offers()).isEmpty(); // Never invent a price to feature a photo.
        assertThat(service.search(request(filter("state", "RS")), 0, 1).content()
            .getFirst().material().materialCode()).isEqualTo("1.1.1");
    }

    @Test void populatedVariationsOutrankEmptyVariationsAndFeaturedQuoteKeepsOwnPhoto() {
        var empty = new CatalogMaterial("1.1.0", "1", "Agregados", "1.1", "Areias",
                "Sem opções", "EM_REVISÃO", "", List.of());
        when(catalog.findAll()).thenReturn(List.of(empty, material));
        assertThat(service.search(request(), 0, 1).content().getFirst().material()).isEqualTo(material);
        var pictured = new Product("photo", "Areia com foto", "Marca", "", "", "", "", "",
            "https://res.cloudinary.com/demo/image/upload/photo.jpg", null,
            Map.of(), List.of(quote("RS", "180", "1.1.1.V01.001")), null, null, "1.1.1", "1.1", "1");
        when(products.findAll()).thenReturn(List.of(product(List.of(quote("RS", "50", null)), "1.1.1"), pictured));
        var result = service.search(request(), 0, 1).content().getFirst();
        assertThat(result.offers().getFirst().productId()).isEqualTo("photo");
        assertThat(result.offers().getFirst().quote().value()).isEqualByComparingTo("180");
        assertThat(result.imageUrl()).isEqualTo(pictured.imageUrl());
    }
}
