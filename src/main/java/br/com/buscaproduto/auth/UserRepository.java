package br.com.buscaproduto.auth;
import org.springframework.data.mongodb.repository.MongoRepository;
public interface UserRepository extends MongoRepository<AppUser, String> {}
