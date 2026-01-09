package dev.mashni.habitsapi.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Rate Limiting Configuration using Bucket4j with Redis.
 * Implements hybrid logic:
 * - Authenticated users: 100 req/min (keyed by userId)
 * - Anonymous users: 20 req/min (keyed by IP address from X-Forwarded-For)
 */
@Configuration
public class RateLimitConfig {

    @Value("${REDIS_URL}")
    private String redisUrl;

    @Bean
    public RedisClient redisClient() {
        RedisURI redisURI = RedisURI.create(redisUrl);
        return RedisClient.create(redisURI);
    }

    @Bean
    public StatefulRedisConnection<String, byte[]> redisConnection(RedisClient redisClient) {
        RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        return redisClient.connect(codec);
    }

    @Bean
    public ProxyManager<String> proxyManager(StatefulRedisConnection<String, byte[]> connection) {
        return LettuceBasedProxyManager.builderFor(connection)
                .build();
    }

    /**
     * Filter that applies rate limiting to all /api/** endpoints.
     * Uses dynamic key resolution based on authentication status.
     */
    @Component
    @Order(1)
    public static class RateLimitFilter implements Filter {

        private final ProxyManager<String> proxyManager;

        // Rate limit configurations
        private static final int AUTHENTICATED_CAPACITY = 100;
        private static final int AUTHENTICATED_REFILL_DURATION_MINUTES = 1;
        private static final int ANONYMOUS_CAPACITY = 20;
        private static final int ANONYMOUS_REFILL_DURATION_MINUTES = 1;

        public RateLimitFilter(ProxyManager<String> proxyManager) {
            this.proxyManager = proxyManager;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {

            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            // Only apply rate limiting to /api/** endpoints
            String requestPath = httpRequest.getRequestURI();
            if (!requestPath.startsWith("/api/")) {
                chain.doFilter(request, response);
                return;
            }

            // Resolve key and configuration based on authentication status
            String bucketKey = resolveKey(httpRequest);
            BucketConfiguration configuration = resolveBucketConfiguration(httpRequest);

            // Get or create bucket for this key
            Supplier<BucketConfiguration> configSupplier = () -> configuration;
            Bucket bucket = proxyManager.builder().build(bucketKey, configSupplier);

            // Try to consume 1 token
            if (bucket.tryConsume(1)) {
                // Request allowed
                chain.doFilter(request, response);
            } else {
                // Rate limit exceeded
                long waitForRefill = bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill() / 1_000_000_000;

                httpResponse.setStatus(429);
                httpResponse.setContentType("application/json");
                httpResponse.setHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));

                String jsonResponse = String.format(
                    "{\"error\":\"Too many requests\",\"status\":429,\"message\":\"Rate limit exceeded. Please try again later.\",\"retryAfter\":%d}",
                    waitForRefill
                );
                httpResponse.getWriter().write(jsonResponse);
            }
        }

        /**
         * Resolves the bucket key based on authentication status.
         * - If authenticated: returns "user:{userId}"
         * - If anonymous: returns "ip:{clientIp}"
         */
        private String resolveKey(HttpServletRequest request) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            // Check if user is authenticated
            if (authentication != null
                && authentication.isAuthenticated()
                && !authentication.getPrincipal().equals("anonymousUser")) {

                // Extract userId from OAuth2User
                if (authentication.getPrincipal() instanceof OAuth2User) {
                    OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
                    String userId = oauth2User.getAttribute("sub"); // Google's user ID
                    if (userId != null) {
                        return "user:" + userId;
                    }
                }
            }

            // Anonymous user - use IP address
            String clientIp = extractClientIp(request);
            return "ip:" + clientIp;
        }

        /**
         * Extracts the real client IP address, considering X-Forwarded-For header
         * (important for apps behind load balancers like Render/Railway).
         */
        private String extractClientIp(HttpServletRequest request) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");

            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                // X-Forwarded-For can contain multiple IPs (client, proxy1, proxy2...)
                // The first one is the real client IP
                String[] ips = xForwardedFor.split(",");
                return ips[0].trim();
            }

            // Fallback to remote address
            return request.getRemoteAddr();
        }

        /**
         * Returns the appropriate bucket configuration based on authentication status.
         * - Authenticated: 100 requests per minute
         * - Anonymous: 20 requests per minute
         */
        private BucketConfiguration resolveBucketConfiguration(HttpServletRequest request) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null
                && authentication.isAuthenticated()
                && !authentication.getPrincipal().equals("anonymousUser")) {

                // Authenticated user: 100 req/min
                return BucketConfiguration.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(AUTHENTICATED_CAPACITY)
                                .refillGreedy(AUTHENTICATED_CAPACITY, Duration.ofMinutes(AUTHENTICATED_REFILL_DURATION_MINUTES))
                                .build())
                        .build();
            } else {
                // Anonymous user: 20 req/min (more restrictive)
                return BucketConfiguration.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(ANONYMOUS_CAPACITY)
                                .refillGreedy(ANONYMOUS_CAPACITY, Duration.ofMinutes(ANONYMOUS_REFILL_DURATION_MINUTES))
                                .build())
                        .build();
            }
        }
    }
}
