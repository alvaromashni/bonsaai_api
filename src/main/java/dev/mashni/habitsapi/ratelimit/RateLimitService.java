package dev.mashni.habitsapi.ratelimit;

import dev.mashni.habitsapi.user.UserPlan;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final RateLimitProperties properties;
    private final ProxyManager<String> proxyManager;
    private final Map<String, Bucket> localCache = new ConcurrentHashMap<>();
    private final boolean useRedis;

    public RateLimitService(RateLimitProperties properties, @Nullable ProxyManager<String> proxyManager) {
        this.properties = properties;
        this.proxyManager = proxyManager;
        this.useRedis = proxyManager != null;

        if (useRedis) {
            log.info("Rate limiting initialized with Redis backend");
        } else {
            log.info("Rate limiting initialized with in-memory backend");
        }
    }

    public Bucket resolveBucket(String key, UserPlan userPlan) {
        BucketConfiguration config = createConfiguration(userPlan);
        return resolveBucket(key, config);
    }

    public Bucket resolveBucketForUnauthenticated(String key) {
        BucketConfiguration config = createUnauthenticatedConfiguration();
        return resolveBucket(key, config);
    }

    private Bucket resolveBucket(String key, BucketConfiguration config) {
        if (useRedis) {
            Supplier<BucketConfiguration> configSupplier = () -> config;
            return proxyManager.builder().build(key, configSupplier);
        }
        return localCache.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(config.getBandwidths()[0])
                .build());
    }

    private BucketConfiguration createConfiguration(UserPlan userPlan) {
        int capacity = userPlan == UserPlan.PRO
                ? properties.getProUserRequestsPerHour()
                : properties.getFreeUserRequestsPerHour();

        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, properties.getWindowDuration())
                        .build())
                .build();
    }

    private BucketConfiguration createUnauthenticatedConfiguration() {
        int capacity = properties.getUnauthenticatedRequestsPerHour();
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, properties.getWindowDuration())
                        .build())
                .build();
    }
}
