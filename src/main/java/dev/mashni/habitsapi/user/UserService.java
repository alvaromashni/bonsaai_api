package dev.mashni.habitsapi.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getUserFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("User is not authenticated");
        }

        var oauth2User = (OAuth2User) authentication.getPrincipal();
        var googleId = oauth2User.getAttribute("sub");

        if (googleId == null) {
            throw new IllegalStateException("Google ID not found in authentication");
        }

        return userRepository.findByGoogleId(googleId.toString())
            .orElseThrow(() -> new IllegalStateException("User not found in database"));
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalStateException("User not found in database"));
    }

    @Transactional
    public void upgradeToPro(User user, int durationInDays) {
        logger.info("Upgrading user {} to PRO for {} days", user.getId(), durationInDays);
        user.setUserPlan(UserPlan.PRO);
        userRepository.save(user);
        logger.info("User {} upgraded to PRO successfully", user.getId());
    }
}
