package br.com.buscaproduto.composition;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/planning/projects/{projectId}/labor")
public class LaborController {
    private final MongoTemplate mongo;

    public LaborController(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Document("labor_plans")
    public record LaborPlan(
            @Id String id,
            String userId,
            String projectId,
            String mode,
            List<LaborItem> items,
            Instant updatedAt) {}

    public record LaborItem(
            @NotBlank @Size(max = 32) String code,
            @NotBlank @Size(max = 140) String title,
            @NotBlank @Pattern(regexp = "TEAM|THIRD_PARTY") String source,
            @NotBlank @Pattern(regexp = "TEAM|THIRD_PARTY|BOTH") String origin,
            BigDecimal cost) {}

    public record LaborPlanRequest(
            @NotBlank @Pattern(regexp = "TEAM|THIRD_PARTY|BOTH") String mode,
            @NotNull @Size(max = 200) List<@Valid LaborItem> items) {}

    public record LaborPlanResponse(
            String projectId,
            String mode,
            List<LaborItem> items,
            Instant updatedAt) {}

    @GetMapping
    public LaborPlanResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId) {
        requireProject(jwt.getSubject(), projectId);
        LaborPlan plan = mongo.findOne(planQuery(jwt.getSubject(), projectId), LaborPlan.class);
        if (plan == null) return new LaborPlanResponse(projectId, "", List.of(), null);
        return response(plan);
    }

    @PutMapping
    public LaborPlanResponse save(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String projectId,
            @Valid @RequestBody LaborPlanRequest body) {
        String userId = jwt.getSubject();
        requireProject(userId, projectId);

        String mode = body.mode().trim().toUpperCase(Locale.ROOT);
        LinkedHashMap<String, LaborItem> unique = new LinkedHashMap<>();

        for (LaborItem item : body.items()) {
            String code = item.code().trim();
            String title = item.title().trim();
            String source = item.source().trim().toUpperCase(Locale.ROOT);
            String origin = item.origin().trim().toUpperCase(Locale.ROOT);
            BigDecimal cost = item.cost() == null ? BigDecimal.ZERO : item.cost();

            if (cost.signum() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O custo da mão de obra não pode ser negativo.");
            }

            if ("TEAM".equals(mode) && (!"TEAM".equals(source) || !"TEAM".equals(origin))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No modo Montar equipe, selecione apenas itens de equipe própria.");
            }
            if ("THIRD_PARTY".equals(mode) && (!"THIRD_PARTY".equals(source) || !"THIRD_PARTY".equals(origin))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No modo Contratar terceiro, selecione apenas especialidades terceirizadas.");
            }

            unique.put(code, new LaborItem(code, title, source, origin, cost));
        }

        String id = UUID.nameUUIDFromBytes((userId + "\n" + projectId).getBytes(StandardCharsets.UTF_8)).toString();
        LaborPlan saved = mongo.save(new LaborPlan(id, userId, projectId, mode, List.copyOf(unique.values()), Instant.now()));
        return response(saved);
    }

    private void requireProject(String userId, String projectId) {
        Query query = Query.query(Criteria.where("userId").is(userId))
                .addCriteria(Criteria.where("id").is(projectId));
        if (!mongo.exists(query, PlanningController.Project.class)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Obra não encontrada.");
        }
    }

    private Query planQuery(String userId, String projectId) {
        return Query.query(Criteria.where("userId").is(userId))
                .addCriteria(Criteria.where("projectId").is(projectId));
    }

    private LaborPlanResponse response(LaborPlan plan) {
        List<LaborItem> items = plan.items() == null ? List.of() : plan.items().stream()
                .map(item -> new LaborItem(
                        item.code(),
                        item.title(),
                        item.source(),
                        item.origin(),
                        item.cost() == null ? BigDecimal.ZERO : item.cost()))
                .toList();
        return new LaborPlanResponse(plan.projectId(), plan.mode(), items, plan.updatedAt());
    }
}
