package dev.mashni.habitsapi.ratelimit;

import dev.mashni.habitsapi.user.User;
import dev.mashni.habitsapi.user.UserPlan;
import dev.mashni.habitsapi.user.UserService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final UserService userService;

    public RateLimitFilter(RateLimitService rateLimitService, UserService userService) {
        this.rateLimitService = rateLimitService;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        // Skip rate limiting for actuator endpoints
        String requestURI = request.getRequestURI();
        if (requestURI.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Bucket bucket;
        String key;

        if (authentication != null && authentication.isAuthenticated()
            && !"anonymousUser".equals(authentication.getPrincipal())) {
            // Authenticated user - get user and their plan
            try {
                User user = userService.getUserFromAuthentication(authentication);
                key = user.getEmail();
                UserPlan userPlan = user.getUserPlan() != null ? user.getUserPlan() : UserPlan.FREE;
                bucket = rateLimitService.resolveBucket(key, userPlan);
            } catch (Exception e) {
                // If we can't get the user, treat as unauthenticated
                key = getClientIP(request);
                bucket = rateLimitService.resolveBucketForUnauthenticated(key);
            }
        } else {
            // Unauthenticated user - use IP address
            key = getClientIP(request);
            bucket = rateLimitService.resolveBucketForUnauthenticated(key);
        }

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            // Add rate limit headers
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            response.addHeader("X-Rate-Limit-Retry-After-Seconds",
                String.valueOf(probe.getNanosToWaitForRefill() / 1_000_000_000));
            filterChain.doFilter(request, response);
        } else {
            // Rate limit exceeded
            response.setStatus(429);
            response.setContentType("application/json");
            response.addHeader("X-Rate-Limit-Retry-After-Seconds",
                String.valueOf(probe.getNanosToWaitForRefill() / 1_000_000_000));
            response.getWriter().write(
                String.format("{\"error\":\"Too many requests\",\"retryAfterSeconds\":%d}",
                    probe.getNanosToWaitForRefill() / 1_000_000_000)
            );
        }
    }

    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }
        return request.getRemoteAddr();
    }
}
