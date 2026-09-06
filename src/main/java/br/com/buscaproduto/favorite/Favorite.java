package br.com.buscaproduto.favorite;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Document("favorites")
public record Favorite(@Id String id, String userId, String materialCode) {}
