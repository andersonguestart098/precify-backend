package br.com.buscaproduto.favorite;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FavoriteRepository extends MongoRepository<Favorite, String> {
    List<Favorite> findByUserId(String userId);
    List<Favorite> findByUserIdAndEntityType(String userId, String entityType);
}
