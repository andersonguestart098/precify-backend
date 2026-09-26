package br.com.buscaproduto.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import br.com.buscaproduto.model.Product;
import br.com.buscaproduto.repository.ProductRepository;

class ProductServiceTest {
    private final ProductRepository repository = mock(ProductRepository.class);
    private final CatalogService catalog = mock(CatalogService.class);
    private final ProductService service = new ProductService(repository, catalog);

    private Product pendingProduct() {
        var quote = new Product.Quote(BigDecimal.ZERO, "A definir", LocalDate.of(2026, 9, 26), "RS");
        var variation = new Product.Variation("Padrão", quote, "1.1.1.V01", "1.1.1.V01.001");
        return new Product(
                "p1", "Produto antigo", "Marca antiga", "Modelo antigo",
                "Família", "Segmento", "Material", "Descrição antiga",
                null, null, Map.of("unidade", "UN"), List.of(variation),
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                "1.1.1", "1.1", "1");
    }

    @Test
    void adminCanTurnPendingQuoteIntoRealValueAndEditBasicData() {
        when(repository.findById("p1")).thenReturn(Optional.of(pendingProduct()));
        when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = service.updateBasic(
                "p1",
                "Produto atualizado",
                "Nova marca",
                "Novo modelo",
                "Descrição atualizada",
                new BigDecimal("149.90"),
                "Fornecedor RS",
                "1.1.1.V01",
                "1.1.1.V01.001");

        assertEquals("Produto atualizado", updated.name());
        assertEquals("Nova marca", updated.brand());
        assertEquals("Novo modelo", updated.model());
        assertEquals("Descrição atualizada", updated.description());
        assertEquals(new BigDecimal("149.90"), updated.variations().getFirst().quote().value());
        assertEquals("Fornecedor RS", updated.variations().getFirst().quote().supplier());
    }

    @Test
    void zeroQuoteMayRemainPendingWithoutSupplier() {
        when(repository.findById("p1")).thenReturn(Optional.of(pendingProduct()));
        when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = service.updateBasic(
                "p1",
                "Produto",
                "Marca",
                "Modelo",
                "Descrição",
                BigDecimal.ZERO,
                "",
                "1.1.1.V01",
                "1.1.1.V01.001");

        assertEquals(BigDecimal.ZERO, updated.variations().getFirst().quote().value());
        assertEquals("A definir", updated.variations().getFirst().quote().supplier());
    }

    @Test
    void positiveQuoteRequiresSupplier() {
        when(repository.findById("p1")).thenReturn(Optional.of(pendingProduct()));

        var error = assertThrows(IllegalArgumentException.class, () -> service.updateBasic(
                "p1",
                "Produto",
                "Marca",
                "Modelo",
                "Descrição",
                new BigDecimal("10.00"),
                "",
                "1.1.1.V01",
                "1.1.1.V01.001"));

        assertEquals("Informe o fornecedor para uma cotação com valor.", error.getMessage());
        verify(repository, never()).save(any(Product.class));
    }
}
