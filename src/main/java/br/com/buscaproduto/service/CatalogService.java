package br.com.buscaproduto.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.buscaproduto.model.CatalogMaterial;
import br.com.buscaproduto.model.CatalogMaterial.CatalogOption;
import br.com.buscaproduto.model.CatalogMaterial.CatalogVariation;
import br.com.buscaproduto.repository.CatalogMaterialRepository;

@Service
public class CatalogService {
    private static final String CATALOG_RESOURCE = "catalog/prico_variacoes.ndjson.gz.b64";

    private final CatalogMaterialRepository repository;
    private final ObjectMapper objectMapper;

    public CatalogService(CatalogMaterialRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public List<CatalogMaterial> findAll() {
        if (repository.count() == 0) importCatalog();
        return repository.findAllByOrderBySegmentCodeAscFamilyCodeAscMaterialCodeAsc();
    }

    public synchronized long importCatalog() {
        Map<String, MaterialBuilder> materials = new LinkedHashMap<>();
        try (var encoded = new ClassPathResource(CATALOG_RESOURCE).getInputStream()) {
            byte[] compressed = Base64.getMimeDecoder().decode(encoded.readAllBytes());
            try (var gzip = new GZIPInputStream(new java.io.ByteArrayInputStream(compressed));
                    var reader = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    Map<String, Object> row = objectMapper.readValue(line, new TypeReference<>() {});
                    String materialCode = text(row, "Cod. MT");
                    MaterialBuilder material = materials.computeIfAbsent(materialCode, ignored -> new MaterialBuilder(row));
                    material.add(row);
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível importar o catálogo PRICO", exception);
        }

        List<CatalogMaterial> documents = materials.values().stream().map(MaterialBuilder::build).toList();
        repository.saveAll(documents);
        return documents.size();
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static int number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static final class MaterialBuilder {
        private final Map<String, Object> material;
        private final Map<String, VariationBuilder> variations = new LinkedHashMap<>();

        private MaterialBuilder(Map<String, Object> material) { this.material = material; }

        private void add(Map<String, Object> row) {
            String variationCode = text(row, "Cod. VR");
            VariationBuilder variation = variations.computeIfAbsent(variationCode, ignored -> new VariationBuilder(row));
            String optionCode = text(row, "Cod. OP");
            if (optionCode != null && !optionCode.isBlank()) variation.options.putIfAbsent(optionCode, row);
        }

        private CatalogMaterial build() {
            List<CatalogVariation> normalizedVariations = variations.values().stream()
                    .map(VariationBuilder::build)
                    .sorted(Comparator.comparingInt(CatalogVariation::order))
                    .toList();
            return new CatalogMaterial(text(material, "Cod. MT"), text(material, "Cod. SG"),
                    text(material, "SEGMENTO"), text(material, "Cod. FM"), text(material, "FAMÍLIA"),
                    text(material, "MATERIAL"), text(material, "STATUS_MT"), text(material, "OBSERVAÇÃO_MT"),
                    normalizedVariations);
        }
    }

    private static final class VariationBuilder {
        private final Map<String, Object> variation;
        private final Map<String, Map<String, Object>> options = new LinkedHashMap<>();

        private VariationBuilder(Map<String, Object> variation) { this.variation = variation; }

        private CatalogVariation build() {
            List<CatalogOption> normalizedOptions = new ArrayList<>();
            for (Map<String, Object> option : options.values()) {
                normalizedOptions.add(new CatalogOption(text(option, "Cod. OP"), text(option, "OPÇÃO"),
                        text(option, "SÍMBOLO / UNIDADE"), number(option, "ORDEM_OP"), text(option, "STATUS_OP")));
            }
            normalizedOptions.sort(Comparator.comparingInt(CatalogOption::order));
            return new CatalogVariation(text(variation, "Cod. VR"), text(variation, "VARIAÇÃO"),
                    text(variation, "TIPO"), text(variation, "OBRIGATÓRIA"), number(variation, "ORDEM_VR"),
                    List.copyOf(normalizedOptions));
        }
    }
}
