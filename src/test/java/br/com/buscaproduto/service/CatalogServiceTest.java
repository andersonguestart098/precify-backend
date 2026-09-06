package br.com.buscaproduto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.buscaproduto.model.CatalogMaterial;
import br.com.buscaproduto.repository.CatalogMaterialRepository;

class CatalogServiceTest {
    @Test void importsCompleteBundledCatalogWithStableIds() {
        var repository = mock(CatalogMaterialRepository.class);
        List<CatalogMaterial> saved = new ArrayList<>();
        when(repository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<CatalogMaterial> values = invocation.getArgument(0);
            values.forEach(saved::add);
            return saved;
        });
        var service = new CatalogService(repository, new ObjectMapper());
        assertThat(service.importCatalog()).isEqualTo(1648);
        assertThat(saved.stream().map(CatalogMaterial::materialCode).distinct().count()).isEqualTo(1648);
        assertThat(saved.stream().map(CatalogMaterial::segmentCode).distinct().count()).isEqualTo(47);
        assertThat(saved.stream().map(CatalogMaterial::familyCode).distinct().count()).isEqualTo(297);
        assertThat(saved.stream().mapToInt(m -> m.variations().size()).sum()).isEqualTo(3327);
        assertThat(saved.stream().flatMap(m -> m.variations().stream()).mapToInt(v -> v.options().size()).sum()).isEqualTo(1460);
        var codes = saved.stream().map(CatalogMaterial::materialCode).toList();
        saved.clear();
        service.importCatalog();
        assertThat(saved.stream().map(CatalogMaterial::materialCode).toList()).containsExactlyElementsOf(codes);
    }
}
