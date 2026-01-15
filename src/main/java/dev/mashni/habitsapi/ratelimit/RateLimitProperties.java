package dev.mashni.habitsapi.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private int freeUserRequests = 100;
    private int proUserRequests = 1000;
    private int unauthenticatedRequests = 50;
    private Duration windowDuration = Duration.ofHours(1);

    public int getFreeUserRequests() {
        return freeUserRequests;
    }

    public void setFreeUserRequests(int freeUserRequests) {
        this.freeUserRequests = freeUserRequests;
    }

    public int getProUserRequests() {
        return proUserRequests;
    }

    public void setProUserRequests(int proUserRequests) {
        this.proUserRequests = proUserRequests;
    }

    public int getUnauthenticatedRequests() {
        return unauthenticatedRequests;
    }

    public void setUnauthenticatedRequests(int unauthenticatedRequests) {
        this.unauthenticatedRequests = unauthenticatedRequests;
    }

    public Duration getWindowDuration() {
        return windowDuration;
    }

    public void setWindowDuration(Duration windowDuration) {
        this.windowDuration = windowDuration;
    }
}
