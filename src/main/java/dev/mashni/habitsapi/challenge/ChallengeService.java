package dev.mashni.habitsapi.challenge;

import dev.mashni.habitsapi.challenge.dto.*;
import dev.mashni.habitsapi.challenge.model.Challenge;
import dev.mashni.habitsapi.habit.HabitRepository;
import dev.mashni.habitsapi.habit.model.FrequencyType;
import dev.mashni.habitsapi.habit.model.Habit;
import dev.mashni.habitsapi.user.User;
import dev.mashni.habitsapi.user.UserPlan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChallengeService {

    private static final String INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ChallengeRepository challengeRepository;
    private final HabitRepository habitRepository;

    public ChallengeService(ChallengeRepository challengeRepository, HabitRepository habitRepository) {
        this.challengeRepository = challengeRepository;
        this.habitRepository = habitRepository;
    }

    @Transactional
    public ChallengeResponse createChallenge(CreateChallengeRequest request, User user) {
        // Only PRO users can create challenges
        validateProUser(user);

        // Generate unique invite code
        String inviteCode = generateUniqueInviteCode();

        // Create the challenge
        var challenge = new Challenge(
                request.name(),
                request.description(),
                user,
                inviteCode,
                request.startDate(),
                request.endDate()
        );
        challenge = challengeRepository.save(challenge);

        // Create the habit for the creator, linked to this challenge
        var habit = new Habit(
                request.habitName(),
                "Challenge: " + request.name(),
                request.startDate(),
                user,
                FrequencyType.DAILY,
                new HashSet<>()
        );
        habit.setChallenge(challenge);
        habitRepository.save(habit);

        return mapToChallengeResponse(challenge, 1);
    }

    @Transactional
    public ChallengeResponse joinChallenge(JoinChallengeRequest request, User user) {
        // Find challenge by invite code
        var challenge = challengeRepository.findByInviteCode(request.inviteCode().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite code"));

        // Check if user is already a participant
        if (challengeRepository.isUserParticipant(challenge.getId(), user)) {
            throw new IllegalArgumentException("You are already a participant in this challenge");
        }

        // Clone the habit for the new participant
        // Get the habit name from the first existing habit in the challenge
        var existingHabit = challenge.getHabits().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Challenge has no habits"));

        var habit = new Habit(
                existingHabit.getName(),
                "Challenge: " + challenge.getName(),
                challenge.getStartDate(),
                user,
                FrequencyType.DAILY,
                new HashSet<>()
        );
        habit.setChallenge(challenge);
        habitRepository.save(habit);

        int participantCount = challengeRepository.countParticipants(challenge);
        return mapToChallengeResponse(challenge, participantCount);
    }

    @Transactional(readOnly = true)
    public ChallengeDetailResponse getChallengeDetail(UUID challengeId, User user) {
        // Validate user is a participant
        if (!challengeRepository.isUserParticipant(challengeId, user)) {
            throw new IllegalArgumentException("You are not a participant in this challenge");
        }

        var challenge = challengeRepository.findByIdWithHabits(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

        // Get leaderboard
        List<LeaderboardEntryResponse> leaderboard = getLeaderboard(challenge);

        // Calculate today's completion rate
        double todayCompletionRate = calculateTodayCompletionRate(challenge);

        int participantCount = challengeRepository.countParticipants(challenge);

        return new ChallengeDetailResponse(
                challenge.getId(),
                challenge.getName(),
                challenge.getDescription(),
                challenge.getInviteCode(),
                challenge.getStartDate(),
                challenge.getEndDate(),
                challenge.getCreatedAt(),
                challenge.getCreator().getId(),
                challenge.getCreator().getName(),
                participantCount,
                todayCompletionRate,
                leaderboard
        );
    }

    @Transactional(readOnly = true)
    public List<ChallengeResponse> getUserChallenges(User user) {
        var challenges = challengeRepository.findChallengesByParticipant(user);
        return challenges.stream()
                .map(c -> mapToChallengeResponse(c, challengeRepository.countParticipants(c)))
                .collect(Collectors.toList());
    }

    /**
     * Calculate the leaderboard for a challenge.
     * Uses aggregation to count total checks and calculate completion rate.
     */
    private List<LeaderboardEntryResponse> getLeaderboard(Challenge challenge) {
        Map<User, LeaderboardStats> userStats = new HashMap<>();

        LocalDate startDate = challenge.getStartDate();
        LocalDate endDate = challenge.getEndDate() != null ? challenge.getEndDate() : LocalDate.now();
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (totalDays <= 0) totalDays = 1;

        for (Habit habit : challenge.getHabits()) {
            User habitUser = habit.getUser();

            long totalChecks = habit.getLogs().stream()
                    .filter(log -> !log.getCompletedDate().isBefore(startDate))
                    .filter(log -> challenge.getEndDate() == null || !log.getCompletedDate().isAfter(challenge.getEndDate()))
                    .count();

            LocalDate lastCheckIn = habit.getLogs().stream()
                    .map(log -> log.getCompletedDate())
                    .max(LocalDate::compareTo)
                    .orElse(null);

            userStats.put(habitUser, new LeaderboardStats(totalChecks, lastCheckIn));
        }

        final long finalTotalDays = totalDays;
        return userStats.entrySet().stream()
                .map(entry -> {
                    User u = entry.getKey();
                    LeaderboardStats stats = entry.getValue();
                    double completionRate = (stats.totalChecks * 100.0) / finalTotalDays;
                    return new LeaderboardEntryResponse(
                            u.getId(),
                            u.getName(),
                            u.getAvatarUrl(),
                            stats.totalChecks,
                            Math.min(completionRate, 100.0),
                            stats.lastCheckIn
                    );
                })
                .sorted(Comparator.comparingLong(LeaderboardEntryResponse::totalChecks).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Calculate today's completion rate for the challenge.
     */
    private double calculateTodayCompletionRate(Challenge challenge) {
        LocalDate today = LocalDate.now();

        long totalParticipants = challenge.getHabits().size();
        if (totalParticipants == 0) return 0.0;

        long completedToday = challenge.getHabits().stream()
                .filter(habit -> habit.getLogs().stream()
                        .anyMatch(log -> log.getCompletedDate().equals(today)))
                .count();

        return (completedToday * 100.0) / totalParticipants;
    }

    private void validateProUser(User user) {
        if (user.getUserPlan() != UserPlan.PRO) {
            throw new IllegalArgumentException("Only PRO users can create challenges. Upgrade to PRO to unlock this feature.");
        }
    }

    private String generateUniqueInviteCode() {
        String code;
        do {
            code = generateInviteCode();
        } while (challengeRepository.existsByInviteCode(code));
        return code;
    }

    private String generateInviteCode() {
        StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CODE_CHARS.charAt(RANDOM.nextInt(INVITE_CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private ChallengeResponse mapToChallengeResponse(Challenge challenge, int participantCount) {
        return new ChallengeResponse(
                challenge.getId(),
                challenge.getName(),
                challenge.getDescription(),
                challenge.getInviteCode(),
                challenge.getStartDate(),
                challenge.getEndDate(),
                challenge.getCreatedAt(),
                challenge.getCreator().getId(),
                challenge.getCreator().getName(),
                participantCount
        );
    }

    private record LeaderboardStats(long totalChecks, LocalDate lastCheckIn) {}
}
