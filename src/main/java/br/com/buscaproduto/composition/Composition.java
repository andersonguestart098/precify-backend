package br.com.buscaproduto.composition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("compositions")
public record Composition(
        @Id String id,
        @Indexed String userId,
        String name,
        List<Item> items,
        Instant createdAt,
        Instant updatedAt) {

    public record Item(
            String id,
            String materialCode,
            String productId,
            String name,
            String imageUrl,
            String supplier,
            String unit,
            BigDecimal quantity,
            BigDecimal unitPrice) {
    }
}
