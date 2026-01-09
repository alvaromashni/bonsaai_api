package dev.mashni.habitsapi.service;

import dev.mashni.habitsapi.model.User;
import dev.mashni.habitsapi.repository.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        var oauth2User = super.loadUser(userRequest);

        // Extract user info from Google OAuth2 response
        String googleId = oauth2User.getAttribute("sub");
        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");
        String picture = oauth2User.getAttribute("picture");

        if (googleId == null || email == null) {
            throw new OAuth2AuthenticationException("Required user information not found from Google");
        }

        // Find or create user
        var user = userRepository.findByGoogleId(googleId)
            .orElseGet(() -> {
                var newUser = new User(email, name, googleId);
                if (picture != null) {
                    newUser.setAvatarUrl(picture);
                }
                return userRepository.save(newUser);
            });

        // Update user info if changed
        boolean updated = false;
        if (name != null && !user.getName().equals(name)) {
            user.setName(name);
            updated = true;
        }
        if (email != null && !user.getEmail().equals(email)) {
            user.setEmail(email);
            updated = true;
        }
        if (picture != null && !picture.equals(user.getAvatarUrl())) {
            user.setAvatarUrl(picture);
            updated = true;
        }

        if (updated) {
            userRepository.save(user);
        }

        return oauth2User;
    }
}
