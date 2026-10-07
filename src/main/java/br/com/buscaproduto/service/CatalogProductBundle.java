package br.com.buscaproduto.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import br.com.buscaproduto.model.CatalogProduct;

/**
 * Products consolidated by {@code tools/prico/consolidar_prico.py}: one JSON product per line
 * (gzip) plus a manifest with the batch id, source hash and expected counts.
 */
public record CatalogProductBundle(Manifest manifest, String productsSha256, List<Entry> products) {
    static final String PRODUCTS_RESOURCE = "catalog/prico_produtos.ndjson.gz";
    static final String MANIFEST_RESOURCE = "catalog/prico_produtos.manifest.json";

    public record Manifest(String batchId, String sourceFile, String sourceSha256, String sourceDate,
            String productsSha256, String rule, String observation, Map<String, Integer> counts) {
    }

    /** A bundled product and the SHA-256 of its JSON line, used to skip unchanged documents on re-sync. */
    public record Entry(CatalogProduct product, String contentHash) {
    }

    public static CatalogProductBundle read(ObjectMapper objectMapper) {
        ObjectReader manifestReader = objectMapper.readerFor(Manifest.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        ObjectReader productReader = objectMapper.readerFor(CatalogProduct.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        try (InputStream manifestStream = new ClassPathResource(MANIFEST_RESOURCE).getInputStream();
                InputStream raw = new ClassPathResource(PRODUCTS_RESOURCE).getInputStream()) {
            Manifest manifest = manifestReader.readValue(manifestStream);
            MessageDigest fileDigest = MessageDigest.getInstance("SHA-256");
            List<Entry> entries = new ArrayList<>();
            try (var digesting = new DigestInputStream(raw, fileDigest);
                    var reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(digesting), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    CatalogProduct product = productReader.readValue(line);
                    entries.add(new Entry(product, sha256(line)));
                }
                digesting.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return new CatalogProductBundle(manifest, HexFormat.of().formatHex(fileDigest.digest()), List.copyOf(entries));
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível ler o lote de produtos PRICO", exception);
        }
    }

    public int skuCount() {
        return products.stream().mapToInt(entry -> entry.product().skus() == null ? 0 : entry.product().skus().size()).sum();
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
