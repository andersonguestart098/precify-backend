package br.com.buscaproduto.favorite;
import java.util.*;
import java.util.stream.Collectors;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import br.com.buscaproduto.dto.*;
import br.com.buscaproduto.service.*;
@Validated
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {
    private final FavoriteRepository favorites;
    private final CatalogService catalog;
    private final CatalogSearchService search;
    public FavoriteController(FavoriteRepository favorites, CatalogService catalog, CatalogSearchService search) {
        this.favorites = favorites; this.catalog = catalog; this.search = search;
    }
    @GetMapping public Set<String> list(@AuthenticationPrincipal Jwt jwt) {
        return favorites.findByUserId(jwt.getSubject()).stream().map(Favorite::materialCode).collect(Collectors.toSet());
    }
    public record Selection(boolean favorite) {}
    @PutMapping("/{code}") public Selection set(@AuthenticationPrincipal Jwt jwt, @PathVariable @Pattern(regexp="[0-9]+(\\.[0-9]+)*") String code,
        @RequestBody Selection selection) {
        if (catalog.findAll().stream().noneMatch(m -> m.materialCode().equals(code)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Material não encontrado.");
        String id = jwt.getSubject() + ":" + code;
        if (selection.favorite()) favorites.save(new Favorite(id, jwt.getSubject(), code));
        else favorites.deleteById(id);
        return selection;
    }
    @PostMapping("/search") public CatalogSearchPage search(@AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody SearchRequest request,
        @RequestParam(defaultValue="0") @Min(0) int page,
        @RequestParam(defaultValue="10") @Min(1) @Max(100) int size) {
        return search.search(request, page, size, list(jwt));
    }
}
