package dev.mashni.habitsapi.controller;

import dev.mashni.habitsapi.habit.model.Habit;
import dev.mashni.habitsapi.habit.model.HabitLog;
import dev.mashni.habitsapi.user.User;
import dev.mashni.habitsapi.user.UserPlan;
import dev.mashni.habitsapi.habit.HabitLogRepository;
import dev.mashni.habitsapi.habit.HabitRepository;
import dev.mashni.habitsapi.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("AnalyticsController Integration Tests")
class AnalyticsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HabitRepository habitRepository;

    @Autowired
    private HabitLogRepository habitLogRepository;

    private User freeUser;
    private User proUser;

    @BeforeEach
    void setUp() {
        habitLogRepository.deleteAll();
        habitRepository.deleteAll();
        userRepository.deleteAll();

        // Create FREE user
        freeUser = new User("free@test.com", "Free User", "google-free");
        freeUser.setUserPlan(UserPlan.FREE);
        freeUser = userRepository.save(freeUser);

        // Create PRO user
        proUser = new User("pro@test.com", "Pro User", "google-pro");
        proUser.setUserPlan(UserPlan.PRO);
        proUser = userRepository.save(proUser);
    }

    @Test
    @DisplayName("Security Test: Access without authentication should return 302 redirect")
    void testAccessWithoutAuth_ReturnsRedirect() throws Exception {
        mockMvc.perform(get("/api/analytics"))
            .andExpect(status().isFound());
    }

    @Test
    @DisplayName("Security Test: FREE user accessing analytics should return 403 Forbidden")
    void testFreeUserAccess_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/analytics")
                .with(oauth2Login().oauth2User(createOAuth2User(freeUser))))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Success Test: PRO user can access analytics")
    void testProUserAccess_ReturnsOk() throws Exception {
        mockMvc.perform(get("/api/analytics")
                .with(oauth2Login().oauth2User(createOAuth2User(proUser))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kpis").exists())
            .andExpect(jsonPath("$.kpis.completionRate.value").exists())
            .andExpect(jsonPath("$.kpis.completionRate.trend").exists())
            .andExpect(jsonPath("$.kpis.totalHabits.value").exists())
            .andExpect(jsonPath("$.kpis.totalHabits.trend").exists())
            .andExpect(jsonPath("$.kpis.currentStreak.value").exists())
            .andExpect(jsonPath("$.kpis.currentStreak.trend").exists())
            .andExpect(jsonPath("$.heatmapSeries").isArray())
            .andExpect(jsonPath("$.radarSeries").isArray())
            .andExpect(jsonPath("$.radarSeries.length()").value(7)); // 7 days of week
    }

    @Test
    @DisplayName("Data Test: PRO user with habits should receive correct analytics")
    void testProUserWithHabits_ReturnsCorrectAnalytics() throws Exception {
        // Create a habit for PRO user
        Habit habit = new Habit("Read Books", "Daily reading", LocalDate.now().minusDays(5), proUser);
        habit = habitRepository.save(habit);

        // Create some logs
        habitLogRepository.save(new HabitLog(habit, LocalDate.now().minusDays(2)));
        habitLogRepository.save(new HabitLog(habit, LocalDate.now().minusDays(1)));
        habitLogRepository.save(new HabitLog(habit, LocalDate.now()));

        mockMvc.perform(get("/api/analytics")
                .with(oauth2Login().oauth2User(createOAuth2User(proUser))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kpis.totalHabits.value").value(3))
            .andExpect(jsonPath("$.kpis.currentStreak.value").value(3))
            .andExpect(jsonPath("$.heatmapSeries").isArray())
            .andExpect(jsonPath("$.radarSeries").isArray())
            .andExpect(jsonPath("$.radarSeries.length()").value(7));
    }

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
