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

/**
 * Rate limiting filter that applies to all /api/** endpoints.
 * Uses different limits based on user authentication status and plan:
 * - PRO users: Higher limit (configurable)
 * - FREE users: Standard limit (configurable)
 * - Unauthenticated: Lowest limit (configurable)
 */
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
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String key = resolveKey(request);
        Bucket bucket = resolveBucket(request, key);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            addRateLimitHeaders(response, probe);
            filterChain.doFilter(request, response);
        } else {
            handleRateLimitExceeded(response, probe);
        }
    }

    private String resolveKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (isAuthenticated(auth)) {
            try {
                User user = userService.getUserFromAuthentication(auth);
                return "user:" + user.getId();
            } catch (Exception e) {
                // Fallback to IP if user resolution fails
            }
        }

        return "ip:" + extractClientIp(request);
    }

    private Bucket resolveBucket(HttpServletRequest request, String key) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (isAuthenticated(auth)) {
            try {
                User user = userService.getUserFromAuthentication(auth);
                // Use isPro() to check for active PRO plan (considers expiration)
                UserPlan effectivePlan = user.isPro() ? UserPlan.PRO : UserPlan.FREE;
                return rateLimitService.resolveBucket(key, effectivePlan);
            } catch (Exception e) {
                // Fallback to unauthenticated bucket
            }
        }

        return rateLimitService.resolveBucketForUnauthenticated(key);
    }

    private boolean isAuthenticated(Authentication auth) {
        return auth != null
                && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal());
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    private void addRateLimitHeaders(HttpServletResponse response, ConsumptionProbe probe) {
        response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
        long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
        response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(retryAfterSeconds));
    }

    private void handleRateLimitExceeded(HttpServletResponse response, ConsumptionProbe probe) throws IOException {
        long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;

        response.setStatus(429);
        response.setContentType("application/json");
        response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(retryAfterSeconds));
        response.getWriter().write(String.format(
                "{\"error\":\"Too many requests\",\"status\":429,\"message\":\"Rate limit exceeded. Please try again later.\",\"retryAfterSeconds\":%d}",
                retryAfterSeconds
        ));
    }
}
