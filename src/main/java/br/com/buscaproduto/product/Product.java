package br.com.buscaproduto.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("products")
public record Product(
        @Id String id,
        @TextIndexed(weight = 5) String name,
        @TextIndexed(weight = 3) String brand,
        @TextIndexed(weight = 3) String model,
        @Indexed @TextIndexed(weight = 2) String category,
        @TextIndexed String description,
        Map<String, String> attributes,
        Quote quote,
        Instant createdAt,
        Instant updatedAt) {

    public record Quote(BigDecimal value, String supplier, LocalDate date, String region) {}
}
