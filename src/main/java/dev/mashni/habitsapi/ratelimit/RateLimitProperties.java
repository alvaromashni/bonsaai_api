package dev.mashni.habitsapi.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private int freeUserRequestsPerHour = 100;
    private int proUserRequestsPerHour = 1000;
    private int unauthenticatedRequestsPerHour = 50;

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
}
