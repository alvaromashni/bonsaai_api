package dev.mashni.habitsapi.challenge;

import dev.mashni.habitsapi.challenge.model.Challenge;
import dev.mashni.habitsapi.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {

    Optional<Challenge> findByInviteCode(String inviteCode);

    List<Challenge> findByCreator(User creator);

    @Query("SELECT c FROM Challenge c LEFT JOIN FETCH c.habits WHERE c.id = :id")
    Optional<Challenge> findByIdWithHabits(@Param("id") UUID id);

    @Query("SELECT c FROM Challenge c LEFT JOIN FETCH c.habits h WHERE h.user = :user")
    List<Challenge> findChallengesByParticipant(@Param("user") User user);

    @Query("SELECT CASE WHEN COUNT(h) > 0 THEN true ELSE false END FROM Challenge c JOIN c.habits h WHERE c.id = :challengeId AND h.user = :user")
    boolean isUserParticipant(@Param("challengeId") UUID challengeId, @Param("user") User user);

    @Query("SELECT COUNT(DISTINCT h.user) FROM Challenge c JOIN c.habits h WHERE c = :challenge")
    int countParticipants(@Param("challenge") Challenge challenge);

    boolean existsByInviteCode(String inviteCode);
}
