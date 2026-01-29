package dev.mashni.habitsapi.ratelimit;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "rate-limit")
@Validated
public class RateLimitProperties {

    /**
     * Maximum requests per hour for FREE users.
     * Can be overridden via env var: RATE_LIMIT_FREE_USER_REQUESTS_PER_HOUR
     */
    @Min(1)
    private int freeUserRequestsPerHour = 100;

    /**
     * Maximum requests per hour for PRO users.
     * Can be overridden via env var: RATE_LIMIT_PRO_USER_REQUESTS_PER_HOUR
     */
    @Min(1)
    private int proUserRequestsPerHour = 1000;

    /**
     * Maximum requests per hour for unauthenticated users (by IP).
     * Can be overridden via env var: RATE_LIMIT_UNAUTHENTICATED_REQUESTS_PER_HOUR
     */
    @Min(1)
    private int unauthenticatedRequestsPerHour = 50;

    /**
     * Time window for rate limiting.
     */
    @NotNull
    private Duration windowDuration = Duration.ofHours(1);

    public int getFreeUserRequestsPerHour() {
        return freeUserRequestsPerHour;
    }

    public void setFreeUserRequestsPerHour(int freeUserRequestsPerHour) {
        this.freeUserRequestsPerHour = freeUserRequestsPerHour;
    }

    public int getProUserRequestsPerHour() {
        return proUserRequestsPerHour;
    }

    public void setProUserRequestsPerHour(int proUserRequestsPerHour) {
        this.proUserRequestsPerHour = proUserRequestsPerHour;
    }

    public int getUnauthenticatedRequestsPerHour() {
        return unauthenticatedRequestsPerHour;
    }

    public void setUnauthenticatedRequestsPerHour(int unauthenticatedRequestsPerHour) {
        this.unauthenticatedRequestsPerHour = unauthenticatedRequestsPerHour;
    }

    public Duration getWindowDuration() {
        return windowDuration;
    }

    public void setWindowDuration(Duration windowDuration) {
        this.windowDuration = windowDuration;
    }
}
