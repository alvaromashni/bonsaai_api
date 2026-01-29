package dev.mashni.habitsapi.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User("test@example.com", "Test User", "google-123");
        testUser.setId(UUID.randomUUID());
    }

    @Nested
    @DisplayName("upgradeToPro() Tests")
    class UpgradeToProTests {

        @Test
        @DisplayName("Should set expiration from now when user is FREE")
        void upgradeToPro_FreeUser_SetsExpirationFromNow() {
            // Arrange
            testUser.setUserPlan(UserPlan.FREE);
            testUser.setPlanExpiresAt(null);
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            LocalDateTime beforeUpgrade = LocalDateTime.now();

            // Act
            userService.upgradeToPro(testUser, 30);

            // Assert
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();

            assertThat(savedUser.getUserPlan()).isEqualTo(UserPlan.PRO);
            assertThat(savedUser.getPlanExpiresAt()).isNotNull();

            // Check that expiration is approximately 30 days from now (within 2 seconds tolerance)
            LocalDateTime expectedExpiration = beforeUpgrade.plusDays(30);
            assertThat(savedUser.getPlanExpiresAt())
                .isCloseTo(expectedExpiration, within(2, java.time.temporal.ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("Should set expiration from now when user is PRO but expired")
        void upgradeToPro_ExpiredProUser_SetsExpirationFromNow() {
            // Arrange
            testUser.setUserPlan(UserPlan.PRO);
            testUser.setPlanExpiresAt(LocalDateTime.now().minusDays(5)); // Expired 5 days ago
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            LocalDateTime beforeUpgrade = LocalDateTime.now();

            // Act
            userService.upgradeToPro(testUser, 90);

            // Assert
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();

            assertThat(savedUser.getUserPlan()).isEqualTo(UserPlan.PRO);
            assertThat(savedUser.getPlanExpiresAt()).isNotNull();

            // Should start from now (not from expired date)
            LocalDateTime expectedExpiration = beforeUpgrade.plusDays(90);
            assertThat(savedUser.getPlanExpiresAt())
                .isCloseTo(expectedExpiration, within(2, java.time.temporal.ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("Should stack duration when user is PRO and active - Scenario B")
        void upgradeToPro_ActiveProUser_StacksDuration() {
            // Arrange
            LocalDateTime currentExpiration = LocalDateTime.now().plusDays(15);
            testUser.setUserPlan(UserPlan.PRO);
            testUser.setPlanExpiresAt(currentExpiration);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // Act
            userService.upgradeToPro(testUser, 30);

            // Assert
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();

            assertThat(savedUser.getUserPlan()).isEqualTo(UserPlan.PRO);
            assertThat(savedUser.getPlanExpiresAt()).isNotNull();

            // Should add 30 days to current expiration (15 days from now + 30 = 45 days from now)
            LocalDateTime expectedExpiration = currentExpiration.plusDays(30);
            assertThat(savedUser.getPlanExpiresAt()).isEqualTo(expectedExpiration);
        }

        @Test
        @DisplayName("Should stack duration correctly with quarterly plan (90 days)")
        void upgradeToPro_ActiveProUser_StacksQuarterlyDuration() {
            // Arrange
            LocalDateTime currentExpiration = LocalDateTime.now().plusDays(10);
            testUser.setUserPlan(UserPlan.PRO);
            testUser.setPlanExpiresAt(currentExpiration);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // Act
            userService.upgradeToPro(testUser, 90);

            // Assert
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();

            LocalDateTime expectedExpiration = currentExpiration.plusDays(90);
            assertThat(savedUser.getPlanExpiresAt()).isEqualTo(expectedExpiration);
        }

        @Test
        @DisplayName("Should stack duration correctly with yearly plan (365 days)")
        void upgradeToPro_ActiveProUser_StacksYearlyDuration() {
            // Arrange
            LocalDateTime currentExpiration = LocalDateTime.now().plusDays(5);
            testUser.setUserPlan(UserPlan.PRO);
            testUser.setPlanExpiresAt(currentExpiration);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // Act
            userService.upgradeToPro(testUser, 365);

            // Assert
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();

            LocalDateTime expectedExpiration = currentExpiration.plusDays(365);
            assertThat(savedUser.getPlanExpiresAt()).isEqualTo(expectedExpiration);
        }

        @Test
        @DisplayName("Should handle edge case: PRO user with null expiration (corrupted data)")
        void upgradeToPro_ProUserWithNullExpiration_SetsExpirationFromNow() {
            // Arrange
            testUser.setUserPlan(UserPlan.PRO);
            testUser.setPlanExpiresAt(null); // Corrupted state
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            LocalDateTime beforeUpgrade = LocalDateTime.now();

            // Act
            userService.upgradeToPro(testUser, 30);

            // Assert
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();

            // Should treat as FREE user (start from now)
            LocalDateTime expectedExpiration = beforeUpgrade.plusDays(30);
            assertThat(savedUser.getPlanExpiresAt())
                .isCloseTo(expectedExpiration, within(2, java.time.temporal.ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("Example scenario from prompt: expires day 10, buy on day 5, should expire day 10 + 30")
        void upgradeToPro_PromptExample_StacksCorrectly() {
            // Arrange
            // User's plan expires on day 10 of next month
            LocalDateTime expiresOnDay10 = LocalDateTime.now().plusDays(5);
            testUser.setUserPlan(UserPlan.PRO);
            testUser.setPlanExpiresAt(expiresOnDay10);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // Act - User buys +30 days today (day 5 before expiration)
            userService.upgradeToPro(testUser, 30);

            // Assert - New expiration should be day 10 + 30 days
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();

            LocalDateTime expectedExpiration = expiresOnDay10.plusDays(30);
            assertThat(savedUser.getPlanExpiresAt()).isEqualTo(expectedExpiration);
        }

        @Test
        @DisplayName("Should call repository save exactly once")
        void upgradeToPro_CallsRepositorySaveOnce() {
            // Arrange
            testUser.setUserPlan(UserPlan.FREE);
            testUser.setPlanExpiresAt(null);
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // Act
            userService.upgradeToPro(testUser, 30);

            // Assert
            verify(userRepository).save(testUser);
        }
    }
}
