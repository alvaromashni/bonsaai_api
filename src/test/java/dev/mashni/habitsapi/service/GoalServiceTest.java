package dev.mashni.habitsapi.service;

import dev.mashni.habitsapi.goal.dto.CreateGoalRequest;
import dev.mashni.habitsapi.goal.dto.GoalSummaryResponse;
import dev.mashni.habitsapi.goal.dto.UpdateGoalHabitsRequest;
import dev.mashni.habitsapi.goal.Goal;
import dev.mashni.habitsapi.goal.GoalService;
import dev.mashni.habitsapi.goal.GoalStatus;
import dev.mashni.habitsapi.habit.model.Habit;
import dev.mashni.habitsapi.goal.GoalRepository;
import dev.mashni.habitsapi.habit.HabitRepository;
import dev.mashni.habitsapi.user.User;
import dev.mashni.habitsapi.user.UserPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GoalService Unit Tests")
class GoalServiceTest {

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private HabitRepository habitRepository;

    @InjectMocks
    private GoalService goalService;

    private User freeUser;
    private User proUser;
    private Habit testHabit1;
    private Habit testHabit2;
    private Goal testGoal;

    @BeforeEach
    void setUp() {
        // Free user setup
        freeUser = new User("free@example.com", "Free User", "google-free-123");
        freeUser.setId(UUID.randomUUID());
        freeUser.setUserPlan(UserPlan.FREE);

        // Pro user setup
        proUser = new User("pro@example.com", "Pro User", "google-pro-456");
        proUser.setId(UUID.randomUUID());
        proUser.setUserPlan(UserPlan.PRO);

        // Test habits
        testHabit1 = new Habit("Exercise", "Daily exercise", LocalDate.now(), freeUser);
        testHabit1.setId(UUID.randomUUID());

        testHabit2 = new Habit("Read Books", "Daily reading", LocalDate.now(), freeUser);
        testHabit2.setId(UUID.randomUUID());

        // Test goal
        testGoal = new Goal(freeUser, "Lose 10kg", "Weight loss goal", LocalDateTime.now().plusMonths(3));
        testGoal.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("FREE user can create their first goal successfully")
    void testCreateGoal_FreeUser_FirstGoal_Success() {
        // Arrange
        var request = new CreateGoalRequest(
                "Lose 10kg",
                "Weight loss goal",
                LocalDateTime.now().plusMonths(3),
                null
        );

        when(goalRepository.countByUser(freeUser)).thenReturn(0L);
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        var result = goalService.createGoal(request, freeUser);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Lose 10kg");
        assertThat(result.getUser()).isEqualTo(freeUser);
        assertThat(result.getStatus()).isEqualTo(GoalStatus.IN_PROGRESS);
        verify(goalRepository, times(1)).save(any(Goal.class));
    }

    @Test
    @DisplayName("FREE user cannot create second goal - throws exception")
    void testCreateGoal_FreeUser_ExceedsLimit_ThrowsException() {
        // Arrange
        var request = new CreateGoalRequest(
                "Second Goal",
                "This should fail",
                null,
                null
        );

        when(goalRepository.countByUser(freeUser)).thenReturn(1L); // Already has 1 goal

        // Act & Assert
        assertThatThrownBy(() -> goalService.createGoal(request, freeUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Free users can only create 1 goal");

        verify(goalRepository, never()).save(any(Goal.class));
    }

    @Test
    @DisplayName("PRO user can create multiple goals without limit")
    void testCreateGoal_ProUser_MultipleGoals_Success() {
        // Arrange
        var request = new CreateGoalRequest(
                "Fifth Goal",
                "PRO users have no limit",
                null,
                null
        );

        // PRO users don't have limit, so countByUser is not called
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        var result = goalService.createGoal(request, proUser);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Fifth Goal");
        assertThat(result.getUser()).isEqualTo(proUser);
        verify(goalRepository, times(1)).save(any(Goal.class));
        verify(goalRepository, never()).countByUser(proUser); // Should not check limit for PRO
    }

    @Test
    @DisplayName("Create goal with linked habits - validates habits belong to user")
    void testCreateGoal_WithHabits_Success() {
        // Arrange
        Set<UUID> habitIds = Set.of(testHabit1.getId(), testHabit2.getId());
        var request = new CreateGoalRequest(
                "Fitness Goal",
                "Get fit",
                null,
                habitIds
        );

        when(goalRepository.countByUser(freeUser)).thenReturn(0L);
        when(habitRepository.findByIdAndUser(testHabit1.getId(), freeUser))
                .thenReturn(Optional.of(testHabit1));
        when(habitRepository.findByIdAndUser(testHabit2.getId(), freeUser))
                .thenReturn(Optional.of(testHabit2));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        var result = goalService.createGoal(request, freeUser);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getHabits()).hasSize(2);
        assertThat(result.getHabits()).contains(testHabit1, testHabit2);
    }

    @Test
    @DisplayName("Create goal with non-existent habit ID - throws exception")
    void testCreateGoal_WithInvalidHabitId_ThrowsException() {
        // Arrange
        UUID invalidHabitId = UUID.randomUUID();
        Set<UUID> habitIds = Set.of(invalidHabitId);
        var request = new CreateGoalRequest(
                "Test Goal",
                "Should fail",
                null,
                habitIds
        );

        when(goalRepository.countByUser(freeUser)).thenReturn(0L);
        when(habitRepository.findByIdAndUser(invalidHabitId, freeUser))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> goalService.createGoal(request, freeUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found or does not belong to user");

        verify(goalRepository, never()).save(any(Goal.class));
    }

    @Test
    @DisplayName("Create goal with habit from different user - throws exception")
    void testCreateGoal_WithHabitFromDifferentUser_ThrowsException() {
        // Arrange
        User otherUser = new User("other@example.com", "Other User", "google-other-789");
        otherUser.setId(UUID.randomUUID());

        Habit otherUserHabit = new Habit("Other's Habit", "Not mine", LocalDate.now(), otherUser);
        otherUserHabit.setId(UUID.randomUUID());

        Set<UUID> habitIds = Set.of(otherUserHabit.getId());
        var request = new CreateGoalRequest(
                "Test Goal",
                "Should fail",
                null,
                habitIds
        );

        when(goalRepository.countByUser(freeUser)).thenReturn(0L);
        when(habitRepository.findByIdAndUser(otherUserHabit.getId(), freeUser))
                .thenReturn(Optional.empty()); // Habit not found for this user

        // Act & Assert
        assertThatThrownBy(() -> goalService.createGoal(request, freeUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found or does not belong to user");
    }

    @Test
    @DisplayName("Get all goals for user - returns list ordered by created date")
    void testGetAllGoals_Success() {
        // Arrange
        Goal goal1 = new Goal(freeUser, "Goal 1", "First goal", null);
        goal1.setId(UUID.randomUUID());

        Goal goal2 = new Goal(freeUser, "Goal 2", "Second goal", null);
        goal2.setId(UUID.randomUUID());

        when(goalRepository.findByUserOrderByCreatedAtDesc(freeUser))
                .thenReturn(List.of(goal2, goal1)); // Ordered by created_at desc

        // Act
        List<GoalSummaryResponse> result = goalService.getAllGoals(freeUser);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).title()).isEqualTo("Goal 2");
        assertThat(result.get(1).title()).isEqualTo("Goal 1");
    }

    @Test
    @DisplayName("Get goal detail with habits - returns detailed info")
    void testGetGoalDetail_Success() {
        // Arrange
        testGoal.addHabit(testHabit1);
        testGoal.addHabit(testHabit2);

        when(goalRepository.findByIdAndUserWithHabitsAndCheckpoints(testGoal.getId(), freeUser))
                .thenReturn(Optional.of(testGoal));

        // Act
        var result = goalService.getGoalDetail(testGoal.getId(), freeUser);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(testGoal.getId());
        assertThat(result.title()).isEqualTo("Lose 10kg");
        assertThat(result.habits()).hasSize(2);
        assertThat(result.checkpoints()).isEmpty();
    }

    @Test
    @DisplayName("Get goal detail with invalid ID - throws exception")
    void testGetGoalDetail_NotFound_ThrowsException() {
        // Arrange
        UUID invalidId = UUID.randomUUID();
        when(goalRepository.findByIdAndUserWithHabitsAndCheckpoints(invalidId, freeUser))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> goalService.getGoalDetail(invalidId, freeUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Goal not found");
    }

    @Test
    @DisplayName("Update goal habits - replaces habit list successfully")
    void testUpdateGoalHabits_Success() {
        // Arrange
        testGoal.addHabit(testHabit1); // Initially has habit1

        Habit newHabit = new Habit("New Habit", "New one", LocalDate.now(), freeUser);
        newHabit.setId(UUID.randomUUID());

        Set<UUID> newHabitIds = Set.of(testHabit2.getId(), newHabit.getId());
        var request = new UpdateGoalHabitsRequest(newHabitIds);

        when(goalRepository.findByIdAndUserWithHabits(testGoal.getId(), freeUser))
                .thenReturn(Optional.of(testGoal));
        when(habitRepository.findByIdAndUser(testHabit2.getId(), freeUser))
                .thenReturn(Optional.of(testHabit2));
        when(habitRepository.findByIdAndUser(newHabit.getId(), freeUser))
                .thenReturn(Optional.of(newHabit));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        var result = goalService.updateGoalHabits(testGoal.getId(), request, freeUser);

        // Assert
        assertThat(result.getHabits()).hasSize(2);
        assertThat(result.getHabits()).contains(testHabit2, newHabit);
        assertThat(result.getHabits()).doesNotContain(testHabit1); // Old habit removed
    }

    @Test
    @DisplayName("Complete goal - changes status to COMPLETED")
    void testCompleteGoal_Success() {
        // Arrange
        when(goalRepository.findByIdAndUser(testGoal.getId(), freeUser))
                .thenReturn(Optional.of(testGoal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        var result = goalService.completeGoal(testGoal.getId(), freeUser);

        // Assert
        assertThat(result.getStatus()).isEqualTo(GoalStatus.COMPLETED);
        verify(goalRepository, times(1)).save(testGoal);
    }

    @Test
    @DisplayName("Delete goal - removes goal from database")
    void testDeleteGoal_Success() {
        // Arrange
        when(goalRepository.findByIdAndUser(testGoal.getId(), freeUser))
                .thenReturn(Optional.of(testGoal));

        // Act
        goalService.deleteGoal(testGoal.getId(), freeUser);

        // Assert
        verify(goalRepository, times(1)).delete(testGoal);
    }

    @Test
    @DisplayName("Delete goal belonging to different user - throws exception")
    void testDeleteGoal_DifferentUser_ThrowsException() {
        // Arrange
        when(goalRepository.findByIdAndUser(testGoal.getId(), proUser))
                .thenReturn(Optional.empty()); // Goal doesn't belong to proUser

        // Act & Assert
        assertThatThrownBy(() -> goalService.deleteGoal(testGoal.getId(), proUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Goal not found or does not belong to user");

        verify(goalRepository, never()).delete(any(Goal.class));
    }
}
