package br.com.buscaproduto.auth;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AvatarTest {
    @Test void acceptsCloudinaryAndRejectsOtherOrigins() {
        String url = "https://res.cloudinary.com/precify/image/upload/v123/avatar.jpg";
        assertEquals(url, AuthService.validateAvatar(" " + url + " "));
        assertEquals("", AuthService.validateAvatar(null));
        for (String bad : new String[] {"http://res.cloudinary.com/a/image/upload/b",
            "https://res.cloudinary.com.evil.test/a/image/upload/b", "javascript:alert(1)",
            "https://res.cloudinary.com@evil.test/a/image/upload/b", "https://example.com/a.jpg"}) {
            assertThrows(ResponseStatusException.class, () -> AuthService.validateAvatar(bad));
        }
    }
    @Test void editingExistingUserPreservesCredentialsRoleAndCreationTime() {
        var users = mock(UserRepository.class);
        var service = new AuthService(users, mock(PasswordEncoder.class), mock(JwtEncoder.class));
        var old = new AppUser("admin@example.test", "Admin", "existing-hash", AppUser.Role.ADMIN, Instant.EPOCH);
        when(users.findById(old.id())).thenReturn(Optional.of(old));
        when(users.save(any(AppUser.class))).thenAnswer(i -> i.getArgument(0));
        String url = "https://res.cloudinary.com/precify/image/upload/avatar.jpg";
        assertEquals(url, service.updateAvatar(old.id(), url).avatarUrl());
        verify(users).save(new AppUser(old.id(), old.name(), old.passwordHash(), old.role(), old.createdAt(), url));
        assertEquals("", service.view(old).avatarUrl());
        assertEquals("", service.updateAvatar(old.id(), "").avatarUrl());
    }
}
