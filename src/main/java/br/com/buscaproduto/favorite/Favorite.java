package br.com.buscaproduto.favorite;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("favorites")
public record Favorite(
        @Id String id,
        String userId,
        String materialCode,
        String entityType,
        String entityId) {

    public Favorite(String id, String userId, String materialCode) {
        this(id, userId, materialCode, "MATERIAL", materialCode);
    }

    public static Favorite workspace(String userId, String entityType, String entityId) {
        return new Favorite(userId + ":" + entityType + ":" + entityId, userId, null, entityType, entityId);
    }
}
