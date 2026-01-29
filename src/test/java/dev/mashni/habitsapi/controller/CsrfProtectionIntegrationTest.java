package dev.mashni.habitsapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mashni.habitsapi.payment.dto.WooviWebhookPayload;
import dev.mashni.habitsapi.user.User;
import dev.mashni.habitsapi.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("CSRF Protection Integration Tests")
class CsrfProtectionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.findByGoogleId("csrf-test-google-id")
                .orElseGet(() -> {
                    User user = new User("csrf@test.com", "CSRF Test User", "csrf-test-google-id");
                    return userRepository.save(user);
                });
    }

    @Nested
    @DisplayName("Stateful routes (session/cookie) require CSRF token")
    class StatefulRoutesCsrfTests {

        @Test
        @DisplayName("POST /api/habits without CSRF token should return 403")
        void post_NoCsrf_Returns403() throws Exception {
            mockMvc.perform(post("/api/habits")
                    .with(oauth2Login().oauth2User(createOAuth2User(testUser)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Test\",\"startDate\":\"2026-01-01\"}"))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /api/habits with CSRF token should not return 403")
        void post_WithCsrf_NotForbidden() throws Exception {
            mockMvc.perform(post("/api/habits")
                    .with(oauth2Login().oauth2User(createOAuth2User(testUser)))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Test\",\"description\":\"desc\",\"startDate\":\"2026-01-01\"}"))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("GET /api/habits should work without CSRF token (safe method)")
        void get_NoCsrf_Works() throws Exception {
            mockMvc.perform(get("/api/habits")
                    .with(oauth2Login().oauth2User(createOAuth2User(testUser))))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Webhook routes are exempt from CSRF (protected by secret)")
    class WebhookCsrfExemptTests {

        @Test
        @DisplayName("POST /api/webhooks/woovi without CSRF token should NOT return 403")
        void webhook_NoCsrf_NotForbidden() throws Exception {
            var payload = new WooviWebhookPayload(
                "OPENPIX:CHARGE_CREATED",
                new WooviWebhookPayload.Charge("test-corr", 100, "ACTIVE"),
                null
            );

            // Without CSRF token, webhook should not get 403 (it may get 401 from secret check)
            mockMvc.perform(post("/api/webhooks/woovi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized()); // 401 from webhook secret, NOT 403 from CSRF
        }

        @Test
        @DisplayName("POST /api/webhooks/woovi with valid secret should return 200 (no CSRF needed)")
        void webhook_WithSecret_NoCsrf_Returns200() throws Exception {
            var payload = new WooviWebhookPayload(
                "OPENPIX:CHARGE_CREATED",
                new WooviWebhookPayload.Charge("test-corr", 100, "ACTIVE"),
                null
            );

            mockMvc.perform(post("/api/webhooks/woovi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-webhook-secret", "test-webhook-secret")
                    .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
        }
    }

    private OAuth2User createOAuth2User(User user) {
        return new DefaultOAuth2User(
            Collections.emptyList(),
            Map.of(
                "sub", user.getGoogleId(),
                "email", user.getEmail(),
                "name", user.getName()
            ),
            "sub"
        );
    }
}
