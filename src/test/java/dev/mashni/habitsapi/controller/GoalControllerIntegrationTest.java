package dev.mashni.habitsapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mashni.habitsapi.goal.dto.CreateGoalRequest;
import dev.mashni.habitsapi.habit.dto.CreateHabitRequest;
import dev.mashni.habitsapi.goal.dto.UpdateGoalHabitsRequest;
import dev.mashni.habitsapi.user.User;
import dev.mashni.habitsapi.user.UserPlan;
import dev.mashni.habitsapi.goal.GoalRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Goal Controller Integration Tests")
class GoalControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GoalRepository goalRepository;

    private User freeUser;
    private User proUser;

    @BeforeEach
    void setUp() {
        // Clear goals before each test
        goalRepository.deleteAll();

        // Setup FREE user
        freeUser = userRepository.findByGoogleId("test-free-google-id")
                .orElseGet(() -> {
                    User user = new User("freeuser@example.com", "Free User", "test-free-google-id");
                    user.setUserPlan(UserPlan.FREE);
                    return userRepository.save(user);
                });

        // Setup PRO user with valid expiration
        proUser = userRepository.findByGoogleId("test-pro-google-id")
                .orElseGet(() -> {
                    User user = new User("prouser@example.com", "Pro User", "test-pro-google-id");
                    user.setUserPlan(UserPlan.PRO);
                    user.setPlanExpiresAt(LocalDateTime.now().plusDays(30));
                    return userRepository.save(user);
                });
        // Ensure PRO user has valid expiration (update if already exists)
        if (proUser.getPlanExpiresAt() == null || proUser.getPlanExpiresAt().isBefore(LocalDateTime.now())) {
            proUser.setPlanExpiresAt(LocalDateTime.now().plusDays(30));
            proUser = userRepository.save(proUser);
        }
    }

    @Test
    @DisplayName("FREE user can create first goal successfully")
    void testCreateGoal_FreeUser_FirstGoal_Success() throws Exception {
        // Arrange
        var request = new CreateGoalRequest(
                "Lose 10kg",
                "Weight loss goal",
                LocalDateTime.now().plusMonths(3),
                null
        );

        // Act & Assert
        mockMvc.perform(post("/api/goals")
                        .with(oauth2Login().oauth2User(createOAuth2User(freeUser)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Lose 10kg"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.habitCount").value(0));
    }

    @Test
    @DisplayName("FREE user cannot create second goal - returns 400")
    void testCreateGoal_FreeUser_SecondGoal_Fails() throws Exception {
        // Arrange: Create first goal
        var firstRequest = new CreateGoalRequest("First Goal", "Description", null, null);
        mockMvc.perform(post("/api/goals")
                        .with(oauth2Login().oauth2User(createOAuth2User(freeUser)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated());

        // Try to create second goal
        var secondRequest = new CreateGoalRequest("Second Goal", "Should fail", null, null);

        // Act & Assert
        mockMvc.perform(post("/api/goals")
                        .with(oauth2Login().oauth2User(createOAuth2User(freeUser)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isBadRequest()); // IllegalArgumentException -> 400 Bad Request
    }

    @Test
    @DisplayName("PRO user can create multiple goals")
    void testCreateGoal_ProUser_MultipleGoals_Success() throws Exception {
        // Create 3 goals for PRO user
        for (int i = 1; i <= 3; i++) {
            var request = new CreateGoalRequest(
                    "Goal " + i,
                    "Description " + i,
                    null,
                    null
            );

            mockMvc.perform(post("/api/goals")
                            .with(oauth2Login().oauth2User(createOAuth2User(proUser)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.title").value("Goal " + i));
        }

        // Verify all 3 goals exist
        mockMvc.perform(get("/api/goals")
                        .with(oauth2Login().oauth2User(createOAuth2User(proUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("Create goal with linked habits")
    void testCreateGoal_WithHabits_Success() throws Exception {
        // Create habits first
        UUID habitId1 = createHabitAndGetId("Exercise", freeUser);
        UUID habitId2 = createHabitAndGetId("Read", freeUser);

        // Create goal with habits
        var request = new CreateGoalRequest(
                "Fitness Goal",
                "Get fit",
                null,
                Set.of(habitId1, habitId2)
        );

        mockMvc.perform(post("/api/goals")
                        .with(oauth2Login().oauth2User(createOAuth2User(freeUser)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.habitCount").value(2));
    }

    @Test
    @DisplayName("Get all goals returns user's goals only")
    void testGetAllGoals_ReturnsUserGoalsOnly() throws Exception {
        // Create goal for FREE user
        var freeUserRequest = new CreateGoalRequest("Free User Goal", "Description", null, null);
        mockMvc.perform(post("/api/goals")
                        .with(oauth2Login().oauth2User(createOAuth2User(freeUser)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(freeUserRequest)))
                .andExpect(status().isCreated());

        // Create goal for PRO user
        var proUserRequest = new CreateGoalRequest("Pro User Goal", "Description", null, null);
        mockMvc.perform(post("/api/goals")
                        .with(oauth2Login().oauth2User(createOAuth2User(proUser)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proUserRequest)))
                .andExpect(status().isCreated());

        // FREE user should only see their goal
        mockMvc.perform(get("/api/goals")
                        .with(oauth2Login().oauth2User(createOAuth2User(freeUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Free User Goal"));

        // PRO user should only see their goal
        mockMvc.perform(get("/api/goals")
                        .with(oauth2Login().oauth2User(createOAuth2User(proUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Pro User Goal"));
    }

    @Test
    @DisplayName("Get goal detail with habits")
    void testGetGoalDetail_WithHabits_Success() throws Exception {
        // Create habits
        UUID habitId = createHabitAndGetId("Exercise", freeUser);

        // Create goal with habit
        var createRequest = new CreateGoalRequest(
                "Fitness Goal",
                "Get fit",
                null,
                Set.of(habitId)
        );

        MvcResult createResult = mockMvc.perform(post("/api/goals")
                        .with(oauth2Login().oauth2User(createOAuth2User(freeUser)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String goalId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // Get goal detail
        mockMvc.perform(get("/api/goals/" + goalId)
                        .with(oauth2Login().oauth2User(createOAuth2User(freeUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Fitness Goal"))
                .andExpect(jsonPath("$.habits.length()").value(1))
                .andExpect(jsonPath("$.habits[0].name").value("Exercise"));
    }

    @Test
    @DisplayName("Update goal habits - add and remove habits")
    void testUpdateGoalHabits_Success() throws Exception {
        // Create habits
        UUID habit1 = createHabitAndGetId("Habit 1", freeUser);
        UUID habit2 = createHabitAndGetId("Habit 2", freeUser);
        UUID habit3 = createHabitAndGetId("Habit 3", freeUser);

        // Create goal with habit1
        var createRequest = new CreateGoalRequest(
                "Test Goal",
                "Description",
                null,
                Set.of(habit1)
        );

        MvcResult createResult = mockMvc.perform(post("/api/goals")
                        .with(oauth2Login().oauth2User(createOAuth2User(freeUser)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String goalId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // Update to have habit2 and habit3 (removing habit1)
        var updateRequest = new UpdateGoalHabitsRequest(Set.of(habit2, habit3));

        mockMvc.perform(put("/api/goals/" + goalId + "/habits")
                        .with(oauth2Login().oauth2User(createOAuth2User(freeUser)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habits.length()").value(2));
    }

    @Test
    @DisplayName("Complete goal - changes status to COMPLETED")
    void testCompleteGoal_Success() throws Exception {
        // Create goal
        var request = new CreateGoalRequest("Goal to Complete", "Description", null, null);
        MvcResult createResult = mockMvc.perform(post("/api/goals")
                        .with(oauth2Login().oauth2User(createOAuth2User(freeUser)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String goalId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // Complete goal
        mockMvc.perform(patch("/api/goals/" + goalId + "/complete")
                        .with(oauth2Login().oauth2User(createOAuth2User(freeUser)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("Delete goal - removes goal from database")
    void testDeleteGoal_Success() throws Exception {
        // Create goal
        var request = new CreateGoalRequest("Goal to Delete", "Description", null, null);
        MvcResult createResult = mockMvc.perform(post("/api/goals")
                        .with(oauth2Login().oauth2User(createOAuth2User(freeUser)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String goalId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // Delete goal
        mockMvc.perform(delete("/api/goals/" + goalId)
                        .with(oauth2Login().oauth2User(createOAuth2User(freeUser)))
                        .with(csrf()))
                .andExpect(status().isOk());

        // Verify goal is deleted
        mockMvc.perform(get("/api/goals/" + goalId)
                        .with(oauth2Login().oauth2User(createOAuth2User(freeUser))))
                .andExpect(status().isNotFound()); // Goal not found -> 404
    }

    @Test
    @DisplayName("Expired PRO user cannot create multiple goals - treated as FREE")
    void testCreateGoal_ExpiredProUser_LimitedToOneGoal() throws Exception {
        // Create expired PRO user
        User expiredProUser = userRepository.findByGoogleId("test-expired-pro-google-id")
                .orElseGet(() -> {
                    User user = new User("expiredpro@example.com", "Expired Pro User", "test-expired-pro-google-id");
                    user.setUserPlan(UserPlan.PRO);
                    user.setPlanExpiresAt(LocalDateTime.now().minusDays(1)); // Expired yesterday
                    return userRepository.save(user);
                });

        // First goal should succeed
        var firstRequest = new CreateGoalRequest("First Goal", "Description", null, null);
        mockMvc.perform(post("/api/goals")
                        .with(oauth2Login().oauth2User(createOAuth2User(expiredProUser)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated());

        // Second goal should fail (treated as FREE user)
        var secondRequest = new CreateGoalRequest("Second Goal", "Should fail", null, null);
        mockMvc.perform(post("/api/goals")
                        .with(oauth2Login().oauth2User(createOAuth2User(expiredProUser)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isBadRequest()); // IllegalArgumentException -> 400 Bad Request
    }

    @Test
    @DisplayName("User cannot access another user's goal")
    void testDataIsolation_CannotAccessOtherUserGoal() throws Exception {
        // FREE user creates goal
        var request = new CreateGoalRequest("Private Goal", "Description", null, null);
        MvcResult createResult = mockMvc.perform(post("/api/goals")
                        .with(oauth2Login().oauth2User(createOAuth2User(freeUser)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String goalId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // PRO user tries to access FREE user's goal
        mockMvc.perform(get("/api/goals/" + goalId)
                        .with(oauth2Login().oauth2User(createOAuth2User(proUser))))
                .andExpect(status().isNotFound()); // Goal not found for this user -> 404
    }

    // Helper methods
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

    private UUID createHabitAndGetId(String name, User user) throws Exception {
        var habitRequest = new CreateHabitRequest(name, "Description", LocalDate.now(), null, null);

        MvcResult result = mockMvc.perform(post("/api/habits")
                        .with(oauth2Login().oauth2User(createOAuth2User(user)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(habitRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("id").asText()
        );
    }
}
