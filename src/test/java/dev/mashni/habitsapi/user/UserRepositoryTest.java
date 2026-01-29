package dev.mashni.habitsapi.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserRepository Integration Tests")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Nested
    @DisplayName("downgradeExpiredProUsers() Tests")
    class DowngradeExpiredProUsersTests {

        @BeforeEach
        void setUp() {
            // Clean up before each test
            userRepository.deleteAll();
            entityManager.flush();
        }

        @Test
        @DisplayName("Should downgrade PRO users with expired plans")
        void downgradeExpiredProUsers_ExpiredProUsers_DowngradesToFree() {
            // Arrange
            User expiredProUser1 = new User("expired1@example.com", "Expired User 1", "google-1");
            expiredProUser1.setUserPlan(UserPlan.PRO);
            expiredProUser1.setPlanExpiresAt(LocalDateTime.now().minusDays(1)); // Expired yesterday

            User expiredProUser2 = new User("expired2@example.com", "Expired User 2", "google-2");
            expiredProUser2.setUserPlan(UserPlan.PRO);
            expiredProUser2.setPlanExpiresAt(LocalDateTime.now().minusHours(1)); // Expired 1 hour ago

            entityManager.persist(expiredProUser1);
            entityManager.persist(expiredProUser2);
            entityManager.flush();

            // Act
            int downgraded = userRepository.downgradeExpiredProUsers();
            entityManager.clear(); // Clear persistence context to force reload from DB

            // Assert
            assertThat(downgraded).isEqualTo(2);

            User reloadedUser1 = userRepository.findByEmail("expired1@example.com").orElseThrow();
            User reloadedUser2 = userRepository.findByEmail("expired2@example.com").orElseThrow();

            assertThat(reloadedUser1.getUserPlan()).isEqualTo(UserPlan.FREE);
            assertThat(reloadedUser1.getPlanExpiresAt()).isNull();

            assertThat(reloadedUser2.getUserPlan()).isEqualTo(UserPlan.FREE);
            assertThat(reloadedUser2.getPlanExpiresAt()).isNull();
        }

        @Test
        @DisplayName("Should NOT downgrade PRO users with active (future) plans")
        void downgradeExpiredProUsers_ActiveProUsers_RemainsProUnchanged() {
            // Arrange
            User activeProUser = new User("active@example.com", "Active User", "google-3");
            activeProUser.setUserPlan(UserPlan.PRO);
            activeProUser.setPlanExpiresAt(LocalDateTime.now().plusDays(30)); // Valid for 30 more days

            entityManager.persist(activeProUser);
            entityManager.flush();

            // Act
            int downgraded = userRepository.downgradeExpiredProUsers();
            entityManager.clear();

            // Assert
            assertThat(downgraded).isEqualTo(0);

            User reloadedUser = userRepository.findByEmail("active@example.com").orElseThrow();
            assertThat(reloadedUser.getUserPlan()).isEqualTo(UserPlan.PRO);
            assertThat(reloadedUser.getPlanExpiresAt()).isNotNull();
            assertThat(reloadedUser.getPlanExpiresAt()).isAfter(LocalDateTime.now());
        }

        @Test
        @DisplayName("Should NOT downgrade FREE users")
        void downgradeExpiredProUsers_FreeUsers_RemainsUnchanged() {
            // Arrange
            User freeUser = new User("free@example.com", "Free User", "google-4");
            freeUser.setUserPlan(UserPlan.FREE);
            freeUser.setPlanExpiresAt(null);

            entityManager.persist(freeUser);
            entityManager.flush();

            // Act
            int downgraded = userRepository.downgradeExpiredProUsers();
            entityManager.clear();

            // Assert
            assertThat(downgraded).isEqualTo(0);

            User reloadedUser = userRepository.findByEmail("free@example.com").orElseThrow();
            assertThat(reloadedUser.getUserPlan()).isEqualTo(UserPlan.FREE);
        }

        @Test
        @DisplayName("Should handle mixed users correctly - only downgrade expired PRO")
        void downgradeExpiredProUsers_MixedUsers_OnlyDowngradesExpiredPro() {
            // Arrange
            User expiredPro = new User("expired@example.com", "Expired Pro", "google-5");
            expiredPro.setUserPlan(UserPlan.PRO);
            expiredPro.setPlanExpiresAt(LocalDateTime.now().minusDays(5));

            User activePro = new User("active@example.com", "Active Pro", "google-6");
            activePro.setUserPlan(UserPlan.PRO);
            activePro.setPlanExpiresAt(LocalDateTime.now().plusDays(15));

            User freeUser = new User("free@example.com", "Free User", "google-7");
            freeUser.setUserPlan(UserPlan.FREE);
            freeUser.setPlanExpiresAt(null);

            entityManager.persist(expiredPro);
            entityManager.persist(activePro);
            entityManager.persist(freeUser);
            entityManager.flush();

            // Act
            int downgraded = userRepository.downgradeExpiredProUsers();
            entityManager.clear();

            // Assert
            assertThat(downgraded).isEqualTo(1);

            // Verify expired PRO was downgraded
            User reloadedExpired = userRepository.findByEmail("expired@example.com").orElseThrow();
            assertThat(reloadedExpired.getUserPlan()).isEqualTo(UserPlan.FREE);
            assertThat(reloadedExpired.getPlanExpiresAt()).isNull();

            // Verify active PRO remains unchanged
            User reloadedActive = userRepository.findByEmail("active@example.com").orElseThrow();
            assertThat(reloadedActive.getUserPlan()).isEqualTo(UserPlan.PRO);
            assertThat(reloadedActive.getPlanExpiresAt()).isNotNull();

            // Verify FREE user remains unchanged
            User reloadedFree = userRepository.findByEmail("free@example.com").orElseThrow();
            assertThat(reloadedFree.getUserPlan()).isEqualTo(UserPlan.FREE);
        }

        @Test
        @DisplayName("Should return 0 when no users exist")
        void downgradeExpiredProUsers_NoUsers_ReturnsZero() {
            // Act
            int downgraded = userRepository.downgradeExpiredProUsers();

            // Assert
            assertThat(downgraded).isEqualTo(0);
        }

        @Test
        @DisplayName("Should return 0 when no expired PRO users exist")
        void downgradeExpiredProUsers_NoExpiredPro_ReturnsZero() {
            // Arrange
            User activePro = new User("active@example.com", "Active Pro", "google-8");
            activePro.setUserPlan(UserPlan.PRO);
            activePro.setPlanExpiresAt(LocalDateTime.now().plusDays(10));

            entityManager.persist(activePro);
            entityManager.flush();

            // Act
            int downgraded = userRepository.downgradeExpiredProUsers();

            // Assert
            assertThat(downgraded).isEqualTo(0);
        }

        @Test
        @DisplayName("Should handle edge case: expiration exactly at current timestamp")
        void downgradeExpiredProUsers_ExpirationAtExactTimestamp_ShouldDowngrade() {
            // Arrange
            User edgeCaseUser = new User("edge@example.com", "Edge Case User", "google-9");
            edgeCaseUser.setUserPlan(UserPlan.PRO);
            // Set expiration to a time very close to now but in the past
            edgeCaseUser.setPlanExpiresAt(LocalDateTime.now().minusNanos(1000));

            entityManager.persist(edgeCaseUser);
            entityManager.flush();

            // Small delay to ensure time has passed
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Act
            int downgraded = userRepository.downgradeExpiredProUsers();
            entityManager.clear();

            // Assert
            assertThat(downgraded).isEqualTo(1);

            User reloadedUser = userRepository.findByEmail("edge@example.com").orElseThrow();
            assertThat(reloadedUser.getUserPlan()).isEqualTo(UserPlan.FREE);
            assertThat(reloadedUser.getPlanExpiresAt()).isNull();
        }

        @Test
        @DisplayName("Should downgrade multiple expired PRO users in bulk")
        void downgradeExpiredProUsers_MultipleExpiredUsers_DowngradesAll() {
            // Arrange - Create 10 expired PRO users
            for (int i = 1; i <= 10; i++) {
                User expiredUser = new User("expired" + i + "@example.com", "Expired User " + i, "google-" + (100 + i));
                expiredUser.setUserPlan(UserPlan.PRO);
                expiredUser.setPlanExpiresAt(LocalDateTime.now().minusDays(i));
                entityManager.persist(expiredUser);
            }
            entityManager.flush();

            // Act
            int downgraded = userRepository.downgradeExpiredProUsers();
            entityManager.clear();

            // Assert
            assertThat(downgraded).isEqualTo(10);

            // Verify all were downgraded
            for (int i = 1; i <= 10; i++) {
                User reloadedUser = userRepository.findByEmail("expired" + i + "@example.com").orElseThrow();
                assertThat(reloadedUser.getUserPlan()).isEqualTo(UserPlan.FREE);
                assertThat(reloadedUser.getPlanExpiresAt()).isNull();
            }
        }
    }
}
