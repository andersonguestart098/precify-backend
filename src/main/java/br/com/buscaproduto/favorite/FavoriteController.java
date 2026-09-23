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
import br.com.buscaproduto.composition.CompositionRepository;
import br.com.buscaproduto.composition.PlanningController;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
@Validated
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {
    private final FavoriteRepository favorites;
    private final CatalogService catalog;
    private final CatalogSearchService search;
    private final CompositionRepository compositions;
    private final MongoTemplate mongo;
    public FavoriteController(FavoriteRepository favorites, CatalogService catalog, CatalogSearchService search,
            CompositionRepository compositions, MongoTemplate mongo) {
        this.favorites = favorites; this.catalog = catalog; this.search = search;
        this.compositions = compositions; this.mongo = mongo;
    }
    @GetMapping public Set<String> list(@AuthenticationPrincipal Jwt jwt) {
        return favorites.findByUserId(jwt.getSubject()).stream()
            .map(Favorite::materialCode)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    @GetMapping("/workspace")
    public Map<String, Set<String>> workspace(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String type : List.of("WORK", "LABOR", "COMPOSITION")) {
            result.put(type, favorites.findByUserIdAndEntityType(jwt.getSubject(), type).stream()
                .map(Favorite::entityId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        }
        return result;
    }
    public record Selection(boolean favorite) {}

    @PutMapping("/workspace/{type}/{id}")
    public Selection setWorkspace(@AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp="WORK|LABOR|COMPOSITION") String type,
            @PathVariable @NotBlank @Size(max=120) String id,
            @RequestBody Selection selection) {
        if (selection.favorite()) {
            if ("WORK".equals(type) && !mongo.exists(Query.query(Criteria.where("userId").is(jwt.getSubject())
                    .and("id").is(id)), PlanningController.Project.class))
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Obra não encontrada.");
            if ("COMPOSITION".equals(type) && compositions.findByIdAndUserId(id, jwt.getSubject()).isEmpty())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Composição não encontrada.");
            if ("LABOR".equals(type) && !id.matches("MO\\.[0-9]+\\.[0-9]+\\.[0-9]+"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código de mão de obra inválido.");
        }
        String favoriteId = jwt.getSubject() + ":" + type + ":" + id;
        if (selection.favorite()) favorites.save(Favorite.workspace(jwt.getSubject(), type, id));
        else favorites.deleteById(favoriteId);
        return selection;
    }

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
