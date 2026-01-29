package dev.mashni.habitsapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mashni.habitsapi.habit.dto.CreateHabitRequest;
import dev.mashni.habitsapi.user.User;
import dev.mashni.habitsapi.habit.HabitRepository;
import dev.mashni.habitsapi.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
@DisplayName("HabitController Integration Tests")
class HabitControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HabitRepository habitRepository;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        habitRepository.deleteAll();
        userRepository.deleteAll();

        // Create test users
        userA = new User("userA@test.com", "User A", "google-userA");
        userA = userRepository.save(userA);

        userB = new User("userB@test.com", "User B", "google-userB");
        userB = userRepository.save(userB);
    }

    @Test
    @DisplayName("Security Test: Access without authentication should return 302 redirect")
    void testAccessWithoutAuth_ReturnsRedirect() throws Exception {
        // Spring Security redirects to login page by default
        mockMvc.perform(get("/api/habits"))
            .andExpect(status().isFound()); // 302 redirect
    }

    @Test
    @DisplayName("Security Test: Authenticated user can access their habits")
    void testAccessWithAuth_ReturnsOk() throws Exception {
        mockMvc.perform(get("/api/habits")
                .with(oauth2Login().oauth2User(createOAuth2User(userA))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Creation Test: UserA creates a habit successfully")
    void testCreateHabit_Success() throws Exception {
        // Arrange
        var request = new CreateHabitRequest(
            "Read Books",
            "Daily reading habit",
            LocalDate.now(),
            null,
            null
        );

        // Act & Assert
        mockMvc.perform(post("/api/habits")
                .with(oauth2Login().oauth2User(createOAuth2User(userA)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Read Books"))
            .andExpect(jsonPath("$.description").value("Daily reading habit"));

        // Verify in database
        var habits = habitRepository.findAll();
        assertThat(habits).hasSize(1);
        assertThat(habits.get(0).getName()).isEqualTo("Read Books");
        assertThat(habits.get(0).getUser().getId()).isEqualTo(userA.getId());
    }

    @Test
    @DisplayName("Data Isolation Test: UserB cannot see UserA's habits")
    void testDataIsolation_UserBCannotSeeUserAHabits() throws Exception {
        // Arrange: UserA creates a habit
        var request = new CreateHabitRequest(
            "UserA's Habit",
            "This belongs to UserA",
            LocalDate.now(),
            null,
            null
        );

        mockMvc.perform(post("/api/habits")
                .with(oauth2Login().oauth2User(createOAuth2User(userA)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        // Act: UserB tries to list habits
        mockMvc.perform(get("/api/habits")
                .with(oauth2Login().oauth2User(createOAuth2User(userB))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content").isEmpty());

        // Verify: UserA can still see their habit
        mockMvc.perform(get("/api/habits")
                .with(oauth2Login().oauth2User(createOAuth2User(userA))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].name").value("UserA's Habit"));
    }

    @Test
    @DisplayName("Data Isolation Test: UserB cannot access UserA's habit by ID")
    void testDataIsolation_UserBCannotAccessUserAHabitById() throws Exception {
        // Arrange: UserA creates a habit
        var request = new CreateHabitRequest(
            "Private Habit",
            "UserA's private habit",
            LocalDate.now(),
            null,
            null
        );

        var result = mockMvc.perform(post("/api/habits")
                .with(oauth2Login().oauth2User(createOAuth2User(userA)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        var habitId = UUID.fromString(
            objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText()
        );

        // Act & Assert: UserB tries to access UserA's habit
        // The GlobalExceptionHandler converts IllegalArgumentException to 404 Not Found
        mockMvc.perform(get("/api/habits/" + habitId)
                .with(oauth2Login().oauth2User(createOAuth2User(userB))))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Toggle Test: Check-in endpoint toggles habit completion")
    void testCheckInToggle_MarksAndUnmarksHabit() throws Exception {
        // Arrange: Create a habit
        var request = new CreateHabitRequest(
            "Exercise",
            "Daily exercise",
            LocalDate.now(),
            null,
            null
        );

        var result = mockMvc.perform(post("/api/habits")
                .with(oauth2Login().oauth2User(createOAuth2User(userA)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        var habitId = UUID.fromString(
            objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText()
        );

        // Act 1: First check-in (mark as done)
        mockMvc.perform(post("/api/habits/" + habitId + "/check")
                .with(oauth2Login().oauth2User(createOAuth2User(userA)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());

        // Assert 1: Verify habit is checked
        mockMvc.perform(get("/api/habits/" + habitId)
                .with(oauth2Login().oauth2User(createOAuth2User(userA))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.daysCompleted").value(1))
            .andExpect(jsonPath("$.currentStreak").value(1));

        // Act 2: Second check-in (unmark/toggle off)
        mockMvc.perform(post("/api/habits/" + habitId + "/check")
                .with(oauth2Login().oauth2User(createOAuth2User(userA)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());

        // Assert 2: Verify habit is unchecked
        mockMvc.perform(get("/api/habits/" + habitId)
                .with(oauth2Login().oauth2User(createOAuth2User(userA))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.daysCompleted").value(0))
            .andExpect(jsonPath("$.currentStreak").value(0));
    }

    @Test
    @DisplayName("Multiple Users Test: Each user has their own independent habits")
    void testMultipleUsers_IndependentHabits() throws Exception {
        // Arrange & Act: UserA creates habit
        var requestA = new CreateHabitRequest("Habit A", "Description A", LocalDate.now(), null, null);
        mockMvc.perform(post("/api/habits")
                .with(oauth2Login().oauth2User(createOAuth2User(userA)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestA)))
            .andExpect(status().isCreated());

        // UserB creates habit
        var requestB = new CreateHabitRequest("Habit B", "Description B", LocalDate.now(), null, null);
        mockMvc.perform(post("/api/habits")
                .with(oauth2Login().oauth2User(createOAuth2User(userB)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestB)))
            .andExpect(status().isCreated());

        // Assert: UserA sees only their habit
        mockMvc.perform(get("/api/habits")
                .with(oauth2Login().oauth2User(createOAuth2User(userA))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].name").value("Habit A"));

        // Assert: UserB sees only their habit
        mockMvc.perform(get("/api/habits")
                .with(oauth2Login().oauth2User(createOAuth2User(userB))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].name").value("Habit B"));

        // Verify total in database
        var allHabits = habitRepository.findAll();
        assertThat(allHabits).hasSize(2);
    }

    @Test
    @DisplayName("Validation Test: Creating habit without required fields fails")
    void testCreateHabit_WithoutRequiredFields_Fails() throws Exception {
        var invalidRequest = Map.of(
            "description", "Missing name and startDate"
        );

        mockMvc.perform(post("/api/habits")
                .with(oauth2Login().oauth2User(createOAuth2User(userA)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest());
    }

    /**
     * Helper method to create a mock OAuth2User for testing
     */
    private OAuth2User createOAuth2User(User user) {
        var attributes = Map.<String, Object>of(
            "sub", user.getGoogleId(),
            "email", user.getEmail(),
            "name", user.getName()
        );

        return new DefaultOAuth2User(
            null,
            attributes,
            "sub"
        );
    }
}
