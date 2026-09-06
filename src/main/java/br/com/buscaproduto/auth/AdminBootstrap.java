package br.com.buscaproduto.auth;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
@Configuration
public class AdminBootstrap {
    @Bean ApplicationRunner createAdmin(UserRepository users, PasswordEncoder passwords,
        @Value("${ADMIN_EMAIL:}") String email, @Value("${ADMIN_PASSWORD:}") String password,
        @Value("${ADMIN_NAME:Administrador}") String name) {
        return args -> {
            if (email.isBlank()) return;
            String id = AuthService.email(email);
            if (users.existsById(id)) return;
            AuthService.validatePassword(password);
            users.insert(new AppUser(id, name, passwords.encode(password), AppUser.Role.ADMIN, Instant.now()));
        };
    }
}
