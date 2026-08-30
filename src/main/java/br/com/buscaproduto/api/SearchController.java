package br.com.buscaproduto.api;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.buscaproduto.search.RankedProduct;
import br.com.buscaproduto.search.SearchRequest;
import br.com.buscaproduto.search.SearchService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final SearchService service;

    public SearchController(SearchService service) {
        this.service = service;
    }

    @PostMapping
    public List<RankedProduct> search(@Valid @RequestBody SearchRequest request) {
        return service.search(request);
    }
}
