package br.com.buscaproduto.composition;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@RestController
@RequestMapping("/api/planning")
public class PlanningController {
    private final MongoTemplate mongo;
    private final CompositionRepository compositions;
    public PlanningController(MongoTemplate mongo, CompositionRepository compositions) { this.mongo = mongo; this.compositions = compositions; }
    @Document("user_projects")
    public record Project(@Id String id, String userId, String name, List<String> compositionIds) {}
    @Document("search_history")
    public record History(@Id String id, String userId, String query, Instant createdAt, int count) {}
    public record ProjectRequest(@NotBlank @Size(max=80) String name, @NotNull @Size(max=200) List<@NotBlank String> compositionIds) {}
    public record SearchRequest(@NotBlank @Size(max=300) String query) {}
    private Query owned(String user) { return Query.query(Criteria.where("userId").is(user)); }
    @GetMapping("/projects")
    public List<Project> projects(@AuthenticationPrincipal Jwt jwt) { return mongo.find(owned(jwt.getSubject()), Project.class); }
    @PostMapping("/projects")
    public Project create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProjectRequest body) { return save(jwt, UUID.randomUUID().toString(), body); }
    @PutMapping("/projects/{id}")
    public Project update(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @Valid @RequestBody ProjectRequest body) {
        if (!mongo.exists(owned(jwt.getSubject()).addCriteria(Criteria.where("id").is(id)), Project.class)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return save(jwt, id, body);
    }
    private Project save(Jwt jwt, String id, ProjectRequest body) {
        for (String compositionId : body.compositionIds())
            if (compositions.findByIdAndUserId(compositionId, jwt.getSubject()).isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Composição não pertence à sua conta.");
        return mongo.save(new Project(id, jwt.getSubject(), body.name().trim(), body.compositionIds().stream().distinct().toList()));
    }
    @DeleteMapping("/projects/{id}")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) { mongo.remove(owned(jwt.getSubject()).addCriteria(Criteria.where("id").is(id)), Project.class); }
    @GetMapping("/history")
    public List<History> history(@AuthenticationPrincipal Jwt jwt) { return mongo.find(owned(jwt.getSubject()).with(org.springframework.data.domain.Sort.by("createdAt").descending()).limit(30), History.class); }
    @PostMapping("/history")
    public History remember(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SearchRequest body) {
        String text = body.query().trim();
        String id = UUID.nameUUIDFromBytes((jwt.getSubject() + "\n" + text.toLowerCase(java.util.Locale.ROOT)).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        History previous = mongo.findById(id, History.class);
        int count = previous == null ? 1 : Math.max(1, previous.count()) + 1;
        var saved = mongo.save(new History(id, jwt.getSubject(), text, Instant.now(), count));
        var stale = mongo.find(owned(jwt.getSubject()).with(org.springframework.data.domain.Sort.by("createdAt").descending()).skip(30), History.class);
        for (var item : stale) mongo.remove(item);
        return saved;
    }
    @DeleteMapping("/history")
    public void clear(@AuthenticationPrincipal Jwt jwt) { mongo.remove(owned(jwt.getSubject()), History.class); }
}
