package dev.mashni.habitsapi.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("User Unit Tests")
class UserTest {

    @Nested
    @DisplayName("isPro() Tests")
    class IsProTests {

        @Test
        @DisplayName("Should return true when user is PRO with valid future expiration")
        void isPro_ProUserWithFutureExpiration_ReturnsTrue() {
            // Arrange
            User user = new User("test@example.com", "Test User", "google-123");
            user.setId(UUID.randomUUID());
            user.setUserPlan(UserPlan.PRO);
            user.setPlanExpiresAt(LocalDateTime.now().plusDays(30));

            // Act
            boolean result = user.isPro();

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false when user is PRO but expiration is null")
        void isPro_ProUserWithNullExpiration_ReturnsFalse() {
            // Arrange
            User user = new User("test@example.com", "Test User", "google-123");
            user.setId(UUID.randomUUID());
            user.setUserPlan(UserPlan.PRO);
            user.setPlanExpiresAt(null);

            // Act
            boolean result = user.isPro();

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false when user is PRO but expiration is in the past")
        void isPro_ProUserWithPastExpiration_ReturnsFalse() {
            // Arrange
            User user = new User("test@example.com", "Test User", "google-123");
            user.setId(UUID.randomUUID());
            user.setUserPlan(UserPlan.PRO);
            user.setPlanExpiresAt(LocalDateTime.now().minusDays(1));

            // Act
            boolean result = user.isPro();

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false when user is FREE")
        void isPro_FreeUser_ReturnsFalse() {
            // Arrange
            User user = new User("test@example.com", "Test User", "google-123");
            user.setId(UUID.randomUUID());
            user.setUserPlan(UserPlan.FREE);
            user.setPlanExpiresAt(null);

            // Act
            boolean result = user.isPro();

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false when user is FREE even with future expiration date set")
        void isPro_FreeUserWithFutureExpiration_ReturnsFalse() {
            // Arrange
            User user = new User("test@example.com", "Test User", "google-123");
            user.setId(UUID.randomUUID());
            user.setUserPlan(UserPlan.FREE);
            user.setPlanExpiresAt(LocalDateTime.now().plusDays(30));

            // Act
            boolean result = user.isPro();

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false when expiration is exactly now (edge case)")
        void isPro_ExpirationExactlyNow_ReturnsFalse() {
            // Arrange
            User user = new User("test@example.com", "Test User", "google-123");
            user.setId(UUID.randomUUID());
            user.setUserPlan(UserPlan.PRO);
            // Set expiration to a time very close to now but in the past
            user.setPlanExpiresAt(LocalDateTime.now().minusNanos(1));

            // Act
            boolean result = user.isPro();

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return true when expiration is 1 second in the future")
        void isPro_ExpirationOneFutureSecond_ReturnsTrue() {
            // Arrange
            User user = new User("test@example.com", "Test User", "google-123");
            user.setId(UUID.randomUUID());
            user.setUserPlan(UserPlan.PRO);
            user.setPlanExpiresAt(LocalDateTime.now().plusSeconds(1));

            // Act
            boolean result = user.isPro();

            // Assert
            assertThat(result).isTrue();
        }
    }
}
