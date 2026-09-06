package br.com.buscaproduto.auth;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }
    public record Register(@NotBlank @Size(max=100) String name, @NotBlank @Email @Size(max=254) String email,
        @NotBlank @Size(min=8, max=72) String password) {}
    public record Login(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=72) String password) {}
    @PostMapping("/register") public AuthService.Session register(@Valid @RequestBody Register r) { return service.register(r.name(), r.email(), r.password()); }
    @PostMapping("/login") public AuthService.Session login(@Valid @RequestBody Login r) { return service.login(r.email(), r.password()); }
    @GetMapping("/me") public AuthService.UserView me(@AuthenticationPrincipal Jwt jwt) { return service.me(jwt.getSubject()); }
}
