package dev.mashni.habitsapi.controller;

import dev.mashni.habitsapi.dto.AnalyticsSummaryDTO;
import dev.mashni.habitsapi.model.UserPlan;
import dev.mashni.habitsapi.service.AnalyticsService;
import dev.mashni.habitsapi.service.UserService;
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

        // Verify user has PRO plan
        if (user.getUserPlan() != UserPlan.PRO) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Analytics feature is only available for PRO users");
        }

        var analytics = analyticsService.getAnalyticsSummary(user);
        return ResponseEntity.ok(analytics);
    }
}
