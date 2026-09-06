package br.com.buscaproduto.auth;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final String dummyHash;
    public AuthService(UserRepository users, PasswordEncoder passwords, JwtEncoder encoder) {
        this.users = users; this.passwords = passwords; this.encoder = encoder;
        this.dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }
    public record UserView(String id, String name, String email, AppUser.Role role) {}
    public record Session(String accessToken, Instant expiresAt, UserView user) {}
    public static String email(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    public UserView view(AppUser u) { return new UserView(u.id(), u.name(), u.id(), u.role()); }
    public UserView me(String id) {
        return view(users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)));
    }
    public Session register(String name, String email, String password) {
        validatePassword(password);
        try {
            return session(users.insert(new AppUser(email(email), name.trim(), passwords.encode(password), AppUser.Role.USER, Instant.now())));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não foi possível cadastrar este e-mail.");
        }
    }
    public Session login(String email, String password) {
        var user = users.findById(email(email)).orElse(null);
        boolean matches = passwords.matches(password, user == null ? dummyHash : user.passwordHash());
        if (user == null || !matches) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.");
        return session(user);
    }
    public static void validatePassword(String password) {
        if (password.length() < 8 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha deve ter pelo menos 8 caracteres e no máximo 72 bytes.");
    }
    private Session session(AppUser user) {
        var now = Instant.now(); var expires = now.plus(1, ChronoUnit.HOURS);
        var claims = JwtClaimsSet.builder().issuer("precify").subject(user.id()).issuedAt(now).expiresAt(expires)
            .claim("roles", List.of(user.role().name())).build();
        var token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new Session(token, expires, view(user));
    }
}
