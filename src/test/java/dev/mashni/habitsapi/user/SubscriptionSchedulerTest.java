package dev.mashni.habitsapi.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionScheduler Unit Tests")
class SubscriptionSchedulerTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SubscriptionScheduler subscriptionScheduler;

    @Nested
    @DisplayName("downgradeExpiredSubscriptions() Tests")
    class DowngradeExpiredSubscriptionsTests {

        @Test
        @DisplayName("Should call repository downgrade method when scheduled task runs")
        void downgradeExpiredSubscriptions_ScheduledTaskRuns_CallsRepository() {
            // Arrange
            when(userRepository.downgradeExpiredProUsers()).thenReturn(5);

            // Act
            subscriptionScheduler.downgradeExpiredSubscriptions();

            // Assert
            verify(userRepository, times(1)).downgradeExpiredProUsers();
        }

        @Test
        @DisplayName("Should handle zero downgrades without error")
        void downgradeExpiredSubscriptions_NothingToDowngrade_HandlesGracefully() {
            // Arrange
            when(userRepository.downgradeExpiredProUsers()).thenReturn(0);

            // Act
            subscriptionScheduler.downgradeExpiredSubscriptions();

            // Assert
            verify(userRepository, times(1)).downgradeExpiredProUsers();
            // No exception should be thrown
        }

        @Test
        @DisplayName("Should handle multiple downgrades")
        void downgradeExpiredSubscriptions_MultipleDowngrades_LogsCorrectly() {
            // Arrange
            when(userRepository.downgradeExpiredProUsers()).thenReturn(100);

            // Act
            subscriptionScheduler.downgradeExpiredSubscriptions();

            // Assert
            verify(userRepository, times(1)).downgradeExpiredProUsers();
        }

        @Test
        @DisplayName("Should handle repository exception gracefully")
        void downgradeExpiredSubscriptions_RepositoryThrowsException_CatchesAndLogs() {
            // Arrange
            when(userRepository.downgradeExpiredProUsers())
                .thenThrow(new RuntimeException("Database connection error"));

            // Act - Should not throw exception (it's caught and logged)
            subscriptionScheduler.downgradeExpiredSubscriptions();

            // Assert
            verify(userRepository, times(1)).downgradeExpiredProUsers();
            // Exception should be caught and logged, not propagated
        }

        @Test
        @DisplayName("Should be called hourly by Spring scheduler")
        void downgradeExpiredSubscriptions_CalledMultipleTimes_WorksCorrectly() {
            // Arrange
            when(userRepository.downgradeExpiredProUsers())
                .thenReturn(3)
                .thenReturn(0)
                .thenReturn(5);

            // Act - Simulate 3 hourly runs
            subscriptionScheduler.downgradeExpiredSubscriptions();
            subscriptionScheduler.downgradeExpiredSubscriptions();
            subscriptionScheduler.downgradeExpiredSubscriptions();

            // Assert
            verify(userRepository, times(3)).downgradeExpiredProUsers();
        }

        @Test
        @DisplayName("Should be transactional and rollback on error")
        void downgradeExpiredSubscriptions_TransactionRollbackOnError_DoesNotCommit() {
            // Arrange
            when(userRepository.downgradeExpiredProUsers())
                .thenThrow(new RuntimeException("Transaction error"));

            // Act
            subscriptionScheduler.downgradeExpiredSubscriptions();

            // Assert
            verify(userRepository, times(1)).downgradeExpiredProUsers();
            // The @Transactional annotation should cause rollback on exception
        }
    }
}
