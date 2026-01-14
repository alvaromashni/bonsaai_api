package dev.mashni.habitsapi.ratelimit;

import dev.mashni.habitsapi.user.UserPlan;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final RateLimitProperties rateLimitProperties;
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    public RateLimitService(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
    }

    public Bucket resolveBucket(String key, UserPlan userPlan) {
        return cache.computeIfAbsent(key, k -> createBucket(userPlan));
    }

    public Bucket resolveBucketForUnauthenticated(String ip) {
        return cache.computeIfAbsent(ip, k -> createUnauthenticatedBucket());
    }

    private Bucket createBucket(UserPlan userPlan) {
        int capacity = userPlan == UserPlan.PRO
            ? rateLimitProperties.getProUserRequestsPerHour()
            : rateLimitProperties.getFreeUserRequestsPerHour();

        Bandwidth limit = Bandwidth.classic(capacity, Refill.intervally(capacity, Duration.ofHours(1)));
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }

    private Bucket createUnauthenticatedBucket() {
        int capacity = rateLimitProperties.getUnauthenticatedRequestsPerHour();
        Bandwidth limit = Bandwidth.classic(capacity, Refill.intervally(capacity, Duration.ofHours(1)));
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }

    public long getAvailableTokens(Bucket bucket) {
        return bucket.getAvailableTokens();
    }
}
