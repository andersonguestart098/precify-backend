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
        assertThrows(ResponseStatusException.class, () -> controller.create(jwt, new PlanningController.ProjectRequest("Obra", "1.1", "Porto Alegre", "Teste", List.of("foreign"))));
        verifyNoInteractions(mongo);
    }
    @Test void savesProjectMetadataAndTrimsText() {
        var mongo = mock(MongoTemplate.class);
        var repository = mock(CompositionRepository.class);
        when(mongo.save(any(PlanningController.Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var jwt = Jwt.withTokenValue("test").header("alg", "none").subject("alice").build();
        var controller = new PlanningController(mongo, repository);

        var saved = controller.create(jwt, new PlanningController.ProjectRequest(
                "  Obra Centro  ", "1.1", "  Porto Alegre / RS  ", "  Observacao da obra  ", List.of()));

        assertEquals("Obra Centro", saved.name());
        assertEquals("1.1", saved.projectType());
        assertEquals("Porto Alegre / RS", saved.location());
        assertEquals("Observacao da obra", saved.notes());
        assertEquals(List.of(), saved.compositionIds());
    }

}
