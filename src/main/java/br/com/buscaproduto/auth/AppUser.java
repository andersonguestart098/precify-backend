package br.com.buscaproduto.auth;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Document("users")
public record AppUser(@Id String id, String name, String passwordHash, Role role, Instant createdAt, String avatarUrl) {
    public AppUser(String id, String name, String passwordHash, Role role, Instant createdAt) {
        this(id, name, passwordHash, role, createdAt, "");
    }
    public enum Role { USER, ADMIN }
}
