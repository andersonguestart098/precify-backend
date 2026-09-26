package br.com.buscaproduto.composition;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

class LaborControllerTest {
    private Jwt jwt() {
        return Jwt.withTokenValue("test").header("alg", "none").subject("alice").build();
    }

    @Test
    void savesLaborPlanInsideOwnedProject() {
        var mongo = mock(MongoTemplate.class);
        when(mongo.exists(any(), eq(PlanningController.Project.class))).thenReturn(true);
        when(mongo.save(any(LaborController.LaborPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var controller = new LaborController(mongo);
        var request = new LaborController.LaborPlanRequest("TEAM", List.of(
                new LaborController.LaborItem("MO.1.3.01", "Pedreiro", "TEAM", "TEAM", new BigDecimal("1850.50"))));

        var result = controller.save(jwt(), "obra-1", request);

        assertEquals("obra-1", result.projectId());
        assertEquals("TEAM", result.mode());
        assertEquals(1, result.items().size());
        assertEquals("Pedreiro", result.items().getFirst().title());
        assertEquals(new BigDecimal("1850.50"), result.items().getFirst().cost());
    }

    @Test
    void rejectsNegativeLaborCost() {
        var mongo = mock(MongoTemplate.class);
        when(mongo.exists(any(), eq(PlanningController.Project.class))).thenReturn(true);

        var controller = new LaborController(mongo);
        var request = new LaborController.LaborPlanRequest("TEAM", List.of(
                new LaborController.LaborItem("MO.1.3.01", "Pedreiro", "TEAM", "TEAM", new BigDecimal("-1"))));

        assertThrows(ResponseStatusException.class, () -> controller.save(jwt(), "obra-1", request));
        verify(mongo, never()).save(any(LaborController.LaborPlan.class));
    }

    @Test
    void rejectsThirdPartyCatalogItemInTeamOnlyMode() {
        var mongo = mock(MongoTemplate.class);
        when(mongo.exists(any(), eq(PlanningController.Project.class))).thenReturn(true);

        var controller = new LaborController(mongo);
        var request = new LaborController.LaborPlanRequest("TEAM", List.of(
                new LaborController.LaborItem("MO.2.1.01", "Sondagem e Geotecnia", "THIRD_PARTY", "THIRD_PARTY", BigDecimal.ZERO)));

        assertThrows(ResponseStatusException.class, () -> controller.save(jwt(), "obra-1", request));
        verify(mongo, never()).save(any(LaborController.LaborPlan.class));
    }
}
