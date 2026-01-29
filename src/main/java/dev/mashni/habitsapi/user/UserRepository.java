package dev.mashni.habitsapi.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    boolean existsByEmail(String email);

    /**
     * Downgrades all PRO users whose subscription has expired to FREE plan.
     * This query sets user_plan to 'FREE' and plan_expires_at to NULL for all users
     * where user_plan is 'PRO' and plan_expires_at is in the past.
     *
     * @return the number of users downgraded
     */
    @Modifying
    @Query("UPDATE User u SET u.userPlan = 'FREE', u.planExpiresAt = NULL " +
           "WHERE u.userPlan = 'PRO' AND u.planExpiresAt < CURRENT_TIMESTAMP")
    int downgradeExpiredProUsers();
}
