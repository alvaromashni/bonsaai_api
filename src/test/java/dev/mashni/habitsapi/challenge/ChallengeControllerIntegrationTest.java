package dev.mashni.habitsapi.challenge;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mashni.habitsapi.challenge.dto.CreateChallengeRequest;
import dev.mashni.habitsapi.challenge.dto.JoinChallengeRequest;
import dev.mashni.habitsapi.challenge.model.Challenge;
import dev.mashni.habitsapi.habit.HabitRepository;
import dev.mashni.habitsapi.habit.model.FrequencyType;
import dev.mashni.habitsapi.habit.model.Habit;
import dev.mashni.habitsapi.user.User;
import dev.mashni.habitsapi.user.UserPlan;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Challenge Controller Integration Tests")
class ChallengeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChallengeRepository challengeRepository;

    @Autowired
    private HabitRepository habitRepository;

    private User proUser;

    @BeforeEach
    void setUp() {
        // Create PRO user with valid expiration
        proUser = userRepository.findByGoogleId("test-pro-challenge-google-id")
                .orElseGet(() -> {
                    User user = new User("prochallenge@example.com", "Pro Challenge User", "test-pro-challenge-google-id");
                    user.setUserPlan(UserPlan.PRO);
                    user.setPlanExpiresAt(LocalDateTime.now().plusDays(30));
                    return userRepository.save(user);
                });

        // Ensure PRO user has valid expiration
        if (proUser.getPlanExpiresAt() == null || proUser.getPlanExpiresAt().isBefore(LocalDateTime.now())) {
            proUser.setPlanExpiresAt(LocalDateTime.now().plusDays(30));
            proUser = userRepository.save(proUser);
        }
    }

    @Nested
    @DisplayName("POST /api/challenges - Create Challenge Date Validation")
    class CreateChallengeDateValidationTests {

        @Test
        @DisplayName("Should return 400 when startDate is in the past")
        void createChallenge_StartDateInPast_Returns400() throws Exception {
            var request = new CreateChallengeRequest(
                    "Test Challenge",
                    "Description",
                    "Daily Exercise",
                    LocalDate.now().minusDays(1), // Yesterday - invalid
                    LocalDate.now().plusDays(30)
            );

            mockMvc.perform(post("/api/challenges")
                            .with(oauth2Login().oauth2User(createOAuth2User(proUser)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when endDate is before startDate")
        void createChallenge_EndDateBeforeStartDate_Returns400() throws Exception {
            var request = new CreateChallengeRequest(
                    "Test Challenge",
                    "Description",
                    "Daily Exercise",
                    LocalDate.now().plusDays(10), // Start in 10 days
                    LocalDate.now().plusDays(5)   // End in 5 days - before start
            );

            mockMvc.perform(post("/api/challenges")
                            .with(oauth2Login().oauth2User(createOAuth2User(proUser)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 201 when startDate is today")
        void createChallenge_StartDateIsToday_Returns201() throws Exception {
            var request = new CreateChallengeRequest(
                    "Today Challenge",
                    "Description",
                    "Daily Exercise",
                    LocalDate.now(), // Today - valid
                    LocalDate.now().plusDays(30)
            );

            mockMvc.perform(post("/api/challenges")
                            .with(oauth2Login().oauth2User(createOAuth2User(proUser)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Today Challenge"));
        }

        @Test
        @DisplayName("Should return 201 when startDate is in the future")
        void createChallenge_StartDateInFuture_Returns201() throws Exception {
            var request = new CreateChallengeRequest(
                    "Future Challenge",
                    "Description",
                    "Daily Exercise",
                    LocalDate.now().plusDays(7), // Next week - valid
                    LocalDate.now().plusDays(37)
            );

            mockMvc.perform(post("/api/challenges")
                            .with(oauth2Login().oauth2User(createOAuth2User(proUser)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Future Challenge"));
        }

        @Test
        @DisplayName("Should return 201 when endDate equals startDate (single day challenge)")
        void createChallenge_EndDateEqualsStartDate_Returns201() throws Exception {
            var request = new CreateChallengeRequest(
                    "Single Day Challenge",
                    "Description",
                    "Daily Exercise",
                    LocalDate.now().plusDays(1),
                    LocalDate.now().plusDays(1) // Same day - valid
            );

            mockMvc.perform(post("/api/challenges")
                            .with(oauth2Login().oauth2User(createOAuth2User(proUser)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Should return 201 when endDate is null (open-ended challenge)")
        void createChallenge_EndDateIsNull_Returns201() throws Exception {
            var request = new CreateChallengeRequest(
                    "Open-Ended Challenge",
                    "Description",
                    "Daily Exercise",
                    LocalDate.now(),
                    null // No end date - valid
            );

            mockMvc.perform(post("/api/challenges")
                            .with(oauth2Login().oauth2User(createOAuth2User(proUser)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("POST /api/challenges/join - Join Challenge Validation")
    class JoinChallengeValidationTests {

        @Test
        @DisplayName("Should return 400 when trying to join an ended challenge")
        void joinChallenge_ChallengeHasEnded_Returns400() throws Exception {
            // Create a challenge that has already ended
            Challenge endedChallenge = new Challenge(
                    "Ended Challenge",
                    "This challenge has ended",
                    proUser,
                    "ENDED1",
                    LocalDate.now().minusDays(30), // Started 30 days ago
                    LocalDate.now().minusDays(1)   // Ended yesterday
            );
            endedChallenge = challengeRepository.save(endedChallenge);

            // Create another user to join
            User joiningUser = new User("joiner@example.com", "Joining User", "google-joiner-123");
            joiningUser = userRepository.save(joiningUser);

            var request = new JoinChallengeRequest("ENDED1");

            mockMvc.perform(post("/api/challenges/join")
                            .with(oauth2Login().oauth2User(createOAuth2User(joiningUser)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest()); // IllegalArgumentException -> 400
        }

        @Test
        @DisplayName("Should return 200 when joining a challenge that ends today")
        void joinChallenge_ChallengeEndsToday_Returns200() throws Exception {
            // Create a challenge that ends today (still joinable)
            Challenge todayEndChallenge = new Challenge(
                    "Today End Challenge",
                    "This challenge ends today",
                    proUser,
                    "TODAY1",
                    LocalDate.now().minusDays(7), // Started a week ago
                    LocalDate.now()               // Ends today - still joinable
            );

            // Create habit for the challenge (required for joining) using bidirectional helper
            Habit creatorHabit = new Habit(
                    "Daily Exercise",
                    "Challenge: Today End Challenge",
                    todayEndChallenge.getStartDate(),
                    proUser,
                    FrequencyType.DAILY,
                    new HashSet<>()
            );
            todayEndChallenge.addHabit(creatorHabit);
            challengeRepository.saveAndFlush(todayEndChallenge);

            // Create another user to join
            User joiningUser = new User("joiner2@example.com", "Joining User 2", "google-joiner-456");
            joiningUser = userRepository.saveAndFlush(joiningUser);

            var request = new JoinChallengeRequest("TODAY1");

            mockMvc.perform(post("/api/challenges/join")
                            .with(oauth2Login().oauth2User(createOAuth2User(joiningUser)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Today End Challenge"));
        }

        @Test
        @DisplayName("Should return 200 when joining an open-ended challenge")
        void joinChallenge_OpenEndedChallenge_Returns200() throws Exception {
            // Create an open-ended challenge (no end date)
            Challenge openChallenge = new Challenge(
                    "Open Challenge",
                    "This challenge has no end date",
                    proUser,
                    "OPEN01",
                    LocalDate.now().minusDays(7),
                    null // No end date
            );

            // Create habit for the challenge (required for joining) using bidirectional helper
            Habit creatorHabit = new Habit(
                    "Daily Exercise",
                    "Challenge: Open Challenge",
                    openChallenge.getStartDate(),
                    proUser,
                    FrequencyType.DAILY,
                    new HashSet<>()
            );
            openChallenge.addHabit(creatorHabit);
            challengeRepository.saveAndFlush(openChallenge);

            // Create another user to join
            User joiningUser = new User("joiner3@example.com", "Joining User 3", "google-joiner-789");
            joiningUser = userRepository.saveAndFlush(joiningUser);

            var request = new JoinChallengeRequest("OPEN01");

            mockMvc.perform(post("/api/challenges/join")
                            .with(oauth2Login().oauth2User(createOAuth2User(joiningUser)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Open Challenge"));
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
