package br.com.buscaproduto.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import br.com.buscaproduto.model.CatalogProduct;

public interface CatalogProductRepository extends MongoRepository<CatalogProduct, String> {
    List<CatalogProduct> findByActiveTrue();

    List<CatalogProduct> findByMaterialCodeAndActiveTrueOrderByProductCodeAsc(String materialCode);

    List<CatalogProduct> findBySkus_SkuCode(String skuCode);

    List<CatalogProduct> findBySkus_Gtin(String gtin);

    List<CatalogProduct> findBySkus_ManufacturerSku(String manufacturerSku);
}
