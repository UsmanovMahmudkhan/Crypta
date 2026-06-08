package com.sovereigncomm.config;

import com.sovereigncomm.security.ApiAuthenticationFilter;
import com.sovereigncomm.security.RateLimitingFilter;
import com.sovereigncomm.security.TokenService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.ProbeController.class)
@Import({SecurityConfig.class, SecurityConfigTest.TestFilters.class, SecurityConfigTest.ProbeController.class})
class SecurityConfigTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void messageEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/messages/direct").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void bootstrapRoleCanAccessOrganizationCreation() throws Exception {
        mockMvc.perform(post("/api/v1/organizations")
                        .header("X-Bootstrap-Token", "bootstrap-secret")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Configuration
    static class TestFilters {
        @Bean
        ApiAuthenticationFilter apiAuthenticationFilter() {
            return new ApiAuthenticationFilter(Mockito.mock(JdbcTemplate.class), new TokenService("unit-pepper"),
                    "bootstrap-secret", false, 15);
        }

        @Bean
        RateLimitingFilter rateLimitingFilter() {
            return new RateLimitingFilter(false, 1);
        }
    }

    @RestController
    public static class ProbeController {
        @GetMapping("/actuator/health")
        Map<String, String> health() {
            return Map.of("status", "UP");
        }

        @PostMapping("/api/v1/messages/direct")
        Map<String, String> message() {
            return Map.of("status", "accepted");
        }

        @PostMapping("/api/v1/organizations")
        Map<String, String> organization() {
            return Map.of("status", "created");
        }
    }
}
