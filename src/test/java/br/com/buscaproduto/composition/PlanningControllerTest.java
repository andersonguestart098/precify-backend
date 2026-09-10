package br.com.buscaproduto.composition;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PlanningControllerTest {
    @Test void rejectsCompositionOutsideAccount() {
        var mongo = mock(MongoTemplate.class);
        var repository = mock(CompositionRepository.class);
        when(repository.findByIdAndUserId("foreign", "alice")).thenReturn(Optional.empty());
        var jwt = Jwt.withTokenValue("test").header("alg", "none").subject("alice").build();
        var controller = new PlanningController(mongo, repository);
        assertThrows(ResponseStatusException.class, () -> controller.create(jwt, new PlanningController.ProjectRequest("Obra", List.of("foreign"))));
        verifyNoInteractions(mongo);
    }
}
