package dev.mashni.habitsapi.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled task that automatically downgrades expired PRO subscriptions to FREE.
 * Runs every hour to check for users whose PRO plan has expired and downgrades them.
 */
@Component
public class SubscriptionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionScheduler.class);

    private final UserRepository userRepository;

    public SubscriptionScheduler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Scheduled task that runs every hour (at the top of the hour) to downgrade expired PRO users.
     * Cron expression: "0 0 * * * *" means:
     * - Second: 0
     * - Minute: 0
     * - Hour: * (every hour)
     * - Day of month: * (every day)
     * - Month: * (every month)
     * - Day of week: * (every day of the week)
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void downgradeExpiredSubscriptions() {
        logger.info("Starting scheduled task: downgrade expired PRO subscriptions");

        try {
            int downgraded = userRepository.downgradeExpiredProUsers();

            if (downgraded > 0) {
                logger.warn("Downgraded {} expired PRO users to FREE plan", downgraded);
            } else {
                logger.debug("No expired PRO subscriptions found");
            }
        } catch (Exception e) {
            logger.error("Error during subscription downgrade task", e);
        }

        logger.info("Completed scheduled task: downgrade expired PRO subscriptions");
    }
}
