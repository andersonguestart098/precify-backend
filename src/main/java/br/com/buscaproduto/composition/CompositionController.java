package br.com.buscaproduto.composition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import br.com.buscaproduto.service.CatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@Validated
@RestController
@RequestMapping("/api/compositions")
public class CompositionController {
    private final CompositionRepository compositions;
    private final CatalogService catalog;

    public CompositionController(CompositionRepository compositions, CatalogService catalog) {
        this.compositions = compositions;
        this.catalog = catalog;
    }

    public record CreateRequest(@NotBlank @Size(max = 80) String name) {}
    public record RenameRequest(@NotBlank @Size(max = 80) String name) {}
    public record QuantityRequest(@NotNull @DecimalMin("0.01") BigDecimal quantity) {}
    public record ItemRequest(
            @NotBlank @Pattern(regexp = "[0-9]+(\\.[0-9]+)*") String materialCode,
            String productId,
            @NotBlank @Size(max = 180) String name,
            @Size(max = 500) String imageUrl,
            @Size(max = 120) String supplier,
            @NotBlank @Size(max = 20) String unit,
            @NotNull @DecimalMin("0.01") BigDecimal quantity,
            @NotNull @PositiveOrZero BigDecimal unitPrice) {}
    public record View(String id, String name, List<Composition.Item> items, BigDecimal total,
            Instant createdAt, Instant updatedAt) {}

    @GetMapping
    public List<View> list(@AuthenticationPrincipal Jwt jwt) {
        return compositions.findByUserIdOrderByUpdatedAtDesc(jwt.getSubject()).stream().map(this::view).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public View create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRequest request) {
        var now = Instant.now();
        var saved = compositions.insert(new Composition(null, jwt.getSubject(), request.name().trim(), List.of(), now, now));
        return view(saved);
    }

    @PatchMapping("/{id}")
    public View rename(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @Valid @RequestBody RenameRequest request) {
        var current = owned(id, jwt);
        return view(compositions.save(new Composition(current.id(), current.userId(), request.name().trim(),
                current.items(), current.createdAt(), Instant.now())));
    }

    @PostMapping("/{id}/items")
    public View addItem(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @Valid @RequestBody ItemRequest request) {
        if (catalog.findAll().stream().noneMatch(material -> material.materialCode().equals(request.materialCode())))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Material não encontrado.");
        var current = owned(id, jwt);
        var items = new ArrayList<>(current.items());
        var existingIndex = findSameItem(items, request);
        if (existingIndex >= 0) {
            var existing = items.get(existingIndex);
            items.set(existingIndex, new Composition.Item(existing.id(), existing.materialCode(), existing.productId(),
                    existing.name(), existing.imageUrl(), existing.supplier(), existing.unit(),
                    existing.quantity().add(request.quantity()), request.unitPrice()));
        } else {
            items.add(new Composition.Item(UUID.randomUUID().toString(), request.materialCode(), blankToNull(request.productId()),
                    request.name().trim(), blankToNull(request.imageUrl()), blankToNull(request.supplier()),
                    request.unit().trim(), request.quantity(), request.unitPrice()));
        }
        return view(save(current, items));
    }

    @PatchMapping("/{id}/items/{itemId}")
    public View quantity(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @PathVariable String itemId,
            @Valid @RequestBody QuantityRequest request) {
        var current = owned(id, jwt);
        var items = new ArrayList<>(current.items());
        int index = indexOf(items, itemId);
        var item = items.get(index);
        items.set(index, new Composition.Item(item.id(), item.materialCode(), item.productId(), item.name(),
                item.imageUrl(), item.supplier(), item.unit(), request.quantity(), item.unitPrice()));
        return view(save(current, items));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public View removeItem(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @PathVariable String itemId) {
        var current = owned(id, jwt);
        var items = new ArrayList<>(current.items());
        items.remove(indexOf(items, itemId));
        return view(save(current, items));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        compositions.delete(owned(id, jwt));
    }

    private Composition owned(String id, Jwt jwt) {
        return compositions.findByIdAndUserId(id, jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Composição não encontrada."));
    }

    private Composition save(Composition current, List<Composition.Item> items) {
        return compositions.save(new Composition(current.id(), current.userId(), current.name(),
                List.copyOf(items), current.createdAt(), Instant.now()));
    }

    private View view(Composition composition) {
        var total = composition.items().stream()
                .map(item -> item.unitPrice().multiply(item.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new View(composition.id(), composition.name(), composition.items(), total,
                composition.createdAt(), composition.updatedAt());
    }

    private static int findSameItem(List<Composition.Item> items, ItemRequest request) {
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            if (item.materialCode().equals(request.materialCode())
                    && java.util.Objects.equals(item.productId(), blankToNull(request.productId()))
                    && java.util.Objects.equals(item.supplier(), blankToNull(request.supplier()))
                    && item.unit().equals(request.unit().trim())) return i;
        }
        return -1;
    }

    private static int indexOf(List<Composition.Item> items, String itemId) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).id().equals(itemId)) return i;
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item não encontrado.");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
