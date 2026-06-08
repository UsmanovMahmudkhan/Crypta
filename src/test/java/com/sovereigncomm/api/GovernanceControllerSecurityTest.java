package com.sovereigncomm.api;

import com.sovereigncomm.config.AuthenticatedActor;
import com.sovereigncomm.cql.CqlPolicyService;
import com.sovereigncomm.security.PlaintextGuard;
import com.sovereigncomm.smalltalk.SmalltalkService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GovernanceControllerSecurityTest {
    private final CqlPolicyService cqlPolicyService = Mockito.mock(CqlPolicyService.class);
    private final SmalltalkService smalltalkService = Mockito.mock(SmalltalkService.class);
    private final GovernanceController controller = new GovernanceController(
            cqlPolicyService,
            smalltalkService,
            new PlaintextGuard(),
            true,
            2000);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bootstrapCannotUseGovernanceControllerDirectly() {
        AuthenticatedActor actor = new AuthenticatedActor(null, null, null, Set.of("BOOTSTRAP"), true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, "N/A"));

        assertThatThrownBy(() -> controller.parseCql("SELECT * FROM DEVICES"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("bearer admin");
    }

    @Test
    void smalltalkRejectsPlaintextKeysNestedInCollectionResults() {
        AuthenticatedActor actor = new AuthenticatedActor(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Set.of("ORG_ADMIN"), false);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, "N/A"));
        GovernanceController.SmalltalkEvaluateRequest request = new GovernanceController.SmalltalkEvaluateRequest();
        request.setScript("policyResult");
        request.setContext(Map.of("policyResult", "ok"));
        Mockito.when(smalltalkService.evaluate("policyResult", request.getContext()))
                .thenReturn(List.of(Map.of("message_body", "not allowed")));

        assertThatThrownBy(() -> controller.evaluateSmalltalk(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message_body");
    }
}
