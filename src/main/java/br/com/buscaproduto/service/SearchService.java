package br.com.buscaproduto.service;

import java.util.List;

import org.springframework.stereotype.Service;

import br.com.buscaproduto.dto.PageResponse;
import br.com.buscaproduto.dto.RankedProduct;
import br.com.buscaproduto.dto.SearchRequest;
import br.com.buscaproduto.model.Product;
import br.com.buscaproduto.repository.ProductRepository;

@Service
public class SearchService {
    private final ProductRepository productRepository;
    private final CompatibilityService compatibilityService;

    public SearchService(ProductRepository productRepository, CompatibilityService compatibilityService) {
        this.productRepository = productRepository;
        this.compatibilityService = compatibilityService;
    }

    public List<RankedProduct> search(SearchRequest request) {
        List<Product> candidates = request.category() == null || request.category().isBlank()
                ? productRepository.findAll()
                : productRepository.findAllByCategoryIgnoreCase(request.category());
        return compatibilityService.rank(candidates, request);
    }

    public PageResponse<RankedProduct> search(SearchRequest request, int page, int size) {
        return PageResponse.from(search(request), page, size);
    }
}
