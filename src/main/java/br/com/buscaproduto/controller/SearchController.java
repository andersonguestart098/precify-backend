package br.com.buscaproduto.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.buscaproduto.dto.RankedProduct;
import br.com.buscaproduto.dto.SearchPageResponse;
import br.com.buscaproduto.dto.SearchRequest;
import br.com.buscaproduto.service.SearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
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

    @PostMapping("/paged")
    public SearchPageResponse searchPaged(
            @Valid @RequestBody SearchRequest request,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return service.search(request, page, size);
    }
}
