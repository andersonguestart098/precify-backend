package br.com.buscaproduto.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import br.com.buscaproduto.model.Product;

public interface ProductRepository extends MongoRepository<Product, String> {
    List<Product> findAllByCategoryIgnoreCase(String category);
}
