package br.com.buscaproduto.search;

import java.util.List;

import org.springframework.stereotype.Service;

import br.com.buscaproduto.product.Product;
import br.com.buscaproduto.product.ProductRepository;

@Service
public class SearchService {
    private final ProductRepository productRepository;
    private final CompatibilityEngine compatibilityEngine;

    public SearchService(ProductRepository productRepository, CompatibilityEngine compatibilityEngine) {
        this.productRepository = productRepository;
        this.compatibilityEngine = compatibilityEngine;
    }

    public List<RankedProduct> search(SearchRequest request) {
        List<Product> candidates = request.category() == null || request.category().isBlank()
                ? productRepository.findAll()
                : productRepository.findAllByCategoryIgnoreCase(request.category());
        return compatibilityEngine.rank(candidates, request);
    }
}
