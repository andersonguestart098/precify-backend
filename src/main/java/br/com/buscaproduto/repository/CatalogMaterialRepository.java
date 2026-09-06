package br.com.buscaproduto.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import br.com.buscaproduto.model.CatalogMaterial;

public interface CatalogMaterialRepository extends MongoRepository<CatalogMaterial, String> {
    List<CatalogMaterial> findAllByOrderBySegmentCodeAscFamilyCodeAscMaterialCodeAsc();
}
