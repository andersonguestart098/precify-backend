package br.com.buscaproduto.composition;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface CompositionRepository extends MongoRepository<Composition, String> {
    List<Composition> findByUserIdOrderByUpdatedAtDesc(String userId);
    Optional<Composition> findByIdAndUserId(String id, String userId);
}
