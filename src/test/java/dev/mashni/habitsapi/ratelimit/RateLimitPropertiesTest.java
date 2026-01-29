package dev.mashni.habitsapi.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimitProperties Binding Tests")
class RateLimitPropertiesTest {

    @Nested
    @DisplayName("Default Values")
    @SpringBootTest
    @ActiveProfiles("test")
    class DefaultValuesTest {

        @Autowired
        private RateLimitProperties properties;

        @Test
        @DisplayName("Should use default values when no properties are set")
        void defaultValues_AreApplied() {
            assertThat(properties.getFreeUserRequestsPerHour()).isEqualTo(100);
            assertThat(properties.getProUserRequestsPerHour()).isEqualTo(1000);
            assertThat(properties.getUnauthenticatedRequestsPerHour()).isEqualTo(50);
            assertThat(properties.getWindowDuration()).isEqualTo(Duration.ofHours(1));
        }
    }

    @Nested
    @DisplayName("Custom Values Override")
    @SpringBootTest
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
            "rate-limit.free-user-requests-per-hour=200",
            "rate-limit.pro-user-requests-per-hour=2000",
            "rate-limit.unauthenticated-requests-per-hour=100",
            "rate-limit.window-duration=PT2H"
    })
    class CustomValuesTest {

        @Autowired
        private RateLimitProperties properties;

        @Test
        @DisplayName("Should override free user requests from properties")
        void freeUserRequests_IsOverridden() {
            assertThat(properties.getFreeUserRequestsPerHour()).isEqualTo(200);
        }

        @Test
        @DisplayName("Should override pro user requests from properties")
        void proUserRequests_IsOverridden() {
            assertThat(properties.getProUserRequestsPerHour()).isEqualTo(2000);
        }

        @Test
        @DisplayName("Should override unauthenticated requests from properties")
        void unauthenticatedRequests_IsOverridden() {
            assertThat(properties.getUnauthenticatedRequestsPerHour()).isEqualTo(100);
        }

        @Test
        @DisplayName("Should override window duration from properties")
        void windowDuration_IsOverridden() {
            assertThat(properties.getWindowDuration()).isEqualTo(Duration.ofHours(2));
        }
    }

    @Nested
    @DisplayName("Environment Variable Format")
    @SpringBootTest
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
            "RATE_LIMIT_FREE_USER_REQUESTS_PER_HOUR=500",
            "RATE_LIMIT_PRO_USER_REQUESTS_PER_HOUR=5000",
            "RATE_LIMIT_UNAUTHENTICATED_REQUESTS_PER_HOUR=250"
    })
    class EnvVarFormatTest {

        @Autowired
        private RateLimitProperties properties;

        @Test
        @DisplayName("Should bind from SCREAMING_SNAKE_CASE env var format")
        void envVarFormat_IsSupported() {
            assertThat(properties.getFreeUserRequestsPerHour()).isEqualTo(500);
            assertThat(properties.getProUserRequestsPerHour()).isEqualTo(5000);
            assertThat(properties.getUnauthenticatedRequestsPerHour()).isEqualTo(250);
        }
    }
}
