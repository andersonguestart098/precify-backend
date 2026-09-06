package br.com.buscaproduto.auth;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final AuthService service;
    public UserController(AuthService service) { this.service = service; }
    public record CreateUser(@NotBlank @Size(max=100) String name, @NotBlank @Email @Size(max=254) String email,
        @NotBlank @Size(min=8,max=72) String password, @NotNull AppUser.Role role) {}
    @GetMapping public List<AuthService.UserView> list() { return service.listUsers(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AuthService.UserView create(@Valid @RequestBody CreateUser request) {
        return service.createUser(request.name(), request.email(), request.password(), request.role());
    }
}
