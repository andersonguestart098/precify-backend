package br.com.buscaproduto.controller;
import java.io.*;
import java.time.Instant;
import java.util.Map;
import javax.imageio.ImageIO;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import br.com.buscaproduto.model.Product;
import br.com.buscaproduto.repository.ProductRepository;

@RestController
public class MediaController {
    private final GridFsTemplate files;
    private final ProductRepository products;
    public MediaController(GridFsTemplate files, ProductRepository products) { this.files = files; this.products = products; }

    @PostMapping("/api/media")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie uma imagem PNG ou JPEG de até 5 MB.");
        byte[] source = file.getBytes();
        byte[] sanitized;
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imagem inválida.");
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg"))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use PNG ou JPEG.");
                reader.setInput(input);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 1 || h < 1 || (long) w * h > 16_000_000)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imagem excede 16 megapixels.");
                var image = reader.read(0);
                var out = new ByteArrayOutputStream();
                ImageIO.write(image, "png", out);
                sanitized = out.toByteArray();
                if (sanitized.length > 10 * 1024 * 1024)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reduza a resolução da imagem.");
            } finally { reader.dispose(); }
        }
        var id = files.store(new ByteArrayInputStream(sanitized), "imagem.png", "image/png",
            new Document("publicImage", true));
        return Map.of("url", "/api/media/" + id.toHexString());
    }

    @GetMapping("/api/media/{id}")
    public ResponseEntity<byte[]> image(@PathVariable String id) throws IOException {
        if (!ObjectId.isValid(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var file = files.findOne(Query.query(Criteria.where("_id").is(new ObjectId(id)).and("metadata.publicImage").is(true)));
        if (file == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        try (var input = files.getResource(file).getInputStream()) {
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore()).body(input.readAllBytes());
        }
    }

    public record Images(String imageUrl, String supplierLogoUrl) {}
    private void validateImage(String url) {
        if (url == null || url.isBlank()) return;
        if (!url.matches("/api/media/[a-fA-F0-9]{24}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie a imagem pelo upload.");
        var id = new ObjectId(url.substring(url.lastIndexOf('/') + 1));
        if (files.findOne(Query.query(Criteria.where("_id").is(id).and("metadata.publicImage").is(true))) == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imagem não encontrada.");
    }
    @PatchMapping("/api/products/{id}/images")
    public Product images(@PathVariable String id, @RequestBody Images images) {
        var p = products.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // Null preserves an existing image; empty string explicitly removes it.
        if (images.imageUrl() != null) validateImage(images.imageUrl());
        if (images.supplierLogoUrl() != null) validateImage(images.supplierLogoUrl());
        var updated = new Product(p.id(), p.name(), p.brand(), p.model(), p.category(), p.segment(), p.material(),
            p.description(), images.imageUrl() == null ? p.imageUrl() : images.imageUrl(),
            images.supplierLogoUrl() == null ? p.supplierLogoUrl() : images.supplierLogoUrl(),
            p.attributes(), p.variations(), p.createdAt(), Instant.now(), p.materialCode(), p.familyCode(), p.segmentCode());
        return products.save(updated);
    }
}
