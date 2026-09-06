package br.com.buscaproduto.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import br.com.buscaproduto.model.*;
import br.com.buscaproduto.repository.ProductRepository;

class ProductCatalogValidationTest {
    final ProductRepository repository = mock(ProductRepository.class);
    final CatalogService catalog = mock(CatalogService.class);
    final ProductService service = new ProductService(repository, catalog);
    ProductCatalogValidationTest() {
        when(catalog.findAll()).thenReturn(List.of(new CatalogMaterial("1.1.1", "1", "Agregados", "1.1",
            "Areias", "Areia fina natural", "EM_REVISÃO", "", List.of(
                new CatalogMaterial.CatalogVariation("1.1.1.V01", "Unidade", "UNIDADE", "SIM", 1,
                    List.of(new CatalogMaterial.CatalogOption("1.1.1.V01.001", "m³", "m³", 1, "EM_REVISÃO")))))));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }
    Product product(String code, String option) {
        return new Product(null, "Areia", "Marca", "Modelo", "Nome incorreto", "Outro", "Outro",
            "Descrição", null, null, Map.of("origem", "Jazida"),
            List.of(new Product.Variation("m³", new Product.Quote(BigDecimal.TEN, "Fornecedor",
                LocalDate.now(), "RS"), "1.1.1.V01", option)), null, null, code, "wrong", "wrong");
    }
    @Test void canonicalNamesAndCodesComeFromServerCatalog() {
        var saved = service.save(product("1.1.1", "1.1.1.V01.001"));
        assertThat(saved.category()).isEqualTo("Areias");
        assertThat(saved.materialCode()).isEqualTo("1.1.1");
        assertThat(saved.familyCode()).isEqualTo("1.1");
        assertThat(saved.segmentCode()).isEqualTo("1");
    }
    @Test void rejectsUnknownMaterialAndCrossMaterialOption() {
        assertThatThrownBy(() -> service.save(product("bad", "1.1.1.V01.001"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save(product("1.1.1", "2.1.1.V01.001"))).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }
}
