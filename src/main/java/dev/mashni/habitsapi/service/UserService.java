package dev.mashni.habitsapi.service;

import dev.mashni.habitsapi.model.User;
import dev.mashni.habitsapi.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class UserService {

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
}
