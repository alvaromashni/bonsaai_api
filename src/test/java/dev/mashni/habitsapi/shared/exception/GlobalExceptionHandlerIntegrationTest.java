package dev.mashni.habitsapi.shared.exception;

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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("GlobalExceptionHandler Integration Tests")
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.findByGoogleId("test-exception-google-id")
                .orElseGet(() -> {
                    User user = new User("exception-test@example.com", "Exception Test User", "test-exception-google-id");
                    return userRepository.save(user);
                });
    }

    @Nested
    @DisplayName("ResourceNotFoundException -> 404 Not Found")
    class ResourceNotFoundTests {

        @Test
        @DisplayName("Should return 404 when habit is not found")
        void habitNotFound_Returns404() throws Exception {
            UUID nonExistentHabitId = UUID.randomUUID();

            mockMvc.perform(get("/api/habits/{habitId}", nonExistentHabitId)
                            .with(oauth2Login().oauth2User(createOAuth2User(testUser)))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.path").exists())
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("Should return 404 when goal is not found")
        void goalNotFound_Returns404() throws Exception {
            UUID nonExistentGoalId = UUID.randomUUID();

            mockMvc.perform(get("/api/goals/{goalId}", nonExistentGoalId)
                            .with(oauth2Login().oauth2User(createOAuth2User(testUser)))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"));
        }

        @Test
        @DisplayName("Should return 404 when payment is not found")
        void paymentNotFound_Returns404() throws Exception {
            UUID nonExistentPaymentId = UUID.randomUUID();

            mockMvc.perform(get("/api/payments/{paymentId}/status", nonExistentPaymentId)
                            .with(oauth2Login().oauth2User(createOAuth2User(testUser)))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"));
        }
    }

    @Nested
    @DisplayName("IllegalArgumentException -> 400 Bad Request")
    class BadRequestTests {

        @Test
        @DisplayName("Should return 400 when creating habit with invalid data")
        void invalidHabitData_Returns400() throws Exception {
            // Trying to create a habit with missing required fields
            String invalidJson = "{}";

            mockMvc.perform(post("/api/habits")
                            .with(oauth2Login().oauth2User(createOAuth2User(testUser)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Nested
    @DisplayName("MethodArgumentNotValidException -> 400 Bad Request with field errors")
    class ValidationExceptionTests {

        @Test
        @DisplayName("Should return 400 with field errors for validation failures")
        void validationFailure_Returns400WithFieldErrors() throws Exception {
            // Create a habit request with blank name (violates @NotBlank)
            String invalidJson = """
                {
                    "name": "",
                    "startDate": "2026-01-29",
                    "frequencyType": "DAILY"
                }
                """;

            mockMvc.perform(post("/api/habits")
                            .with(oauth2Login().oauth2User(createOAuth2User(testUser)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.errors").exists())
                    .andExpect(jsonPath("$.message").value("Validation failed"));
        }
    }

    @Nested
    @DisplayName("Error Response Payload Structure")
    class ErrorResponseStructureTests {

        @Test
        @DisplayName("Should include all required fields in error response")
        void errorResponse_ContainsAllRequiredFields() throws Exception {
            UUID nonExistentId = UUID.randomUUID();

            mockMvc.perform(get("/api/habits/{habitId}", nonExistentId)
                            .with(oauth2Login().oauth2User(createOAuth2User(testUser)))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").isNumber())
                    .andExpect(jsonPath("$.error").isString())
                    .andExpect(jsonPath("$.message").isString())
                    .andExpect(jsonPath("$.path").isString())
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    private OAuth2User createOAuth2User(User user) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", user.getGoogleId());
        attributes.put("email", user.getEmail());
        attributes.put("name", user.getName());

        return new DefaultOAuth2User(
                Collections.emptyList(),
                attributes,
                "sub"
        );
    }
}
