package br.com.buscaproduto.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import br.com.buscaproduto.model.CatalogImport;

public interface CatalogImportRepository extends MongoRepository<CatalogImport, String> {
    List<CatalogImport> findTop20ByOrderByStartedAtDesc();

    boolean existsByProductsSha256AndStatus(String productsSha256, String status);
}
