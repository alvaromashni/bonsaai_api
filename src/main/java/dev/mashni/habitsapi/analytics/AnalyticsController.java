package dev.mashni.habitsapi.analytics;

import dev.mashni.habitsapi.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final UserService userService;

    public AnalyticsController(AnalyticsService analyticsService, UserService userService) {
        this.analyticsService = analyticsService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<?> getAnalytics(Authentication authentication) {
        var user = userService.getUserFromAuthentication(authentication);

        // Verify user has active PRO plan (considers expiration)
        if (!user.isPro()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Analytics feature is only available for PRO users");
        }

        var analytics = analyticsService.getEnhancedAnalytics(user);
        return ResponseEntity.ok(analytics);
    }
}
