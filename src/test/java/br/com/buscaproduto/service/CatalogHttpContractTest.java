package br.com.buscaproduto.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import br.com.buscaproduto.controller.CatalogSearchController;
import br.com.buscaproduto.model.CatalogMaterial;
import br.com.buscaproduto.repository.ProductRepository;

class CatalogHttpContractTest {
    @Test void frontendPayloadReturnsMaterialAndEmptyOffersNotFakeProduct() throws Exception {
        var catalog = mock(CatalogService.class);
        var products = mock(ProductRepository.class);
        when(catalog.findAll()).thenReturn(List.of(new CatalogMaterial("1.1.1", "1", "Agregados",
                "1.1", "Areias", "Areia fina natural", "EM_REVISÃO", "Canônico", List.of())));
        when(products.findAll()).thenReturn(List.of());
        var mvc = MockMvcBuilders.standaloneSetup(new CatalogSearchController(
                new CatalogSearchService(catalog, products))).build();
        mvc.perform(post("/api/catalog/search?page=0&size=10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"familyCode":"","query":"areia","criteria":[],"includeAlternatives":false}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].material.materialCode").value("1.1.1"))
                .andExpect(jsonPath("$.content[0].offers").isEmpty())
                .andExpect(jsonPath("$.content[0].product").doesNotExist());
    }
}
