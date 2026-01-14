package dev.mashni.habitsapi.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration to allow cross-origin requests from the frontend application.
 * This is necessary when frontend and backend run on different ports (e.g., 5173 and 8080).
 */
@Configuration
public class CorsConfig {

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allow requests from frontend URL
        configuration.setAllowedOrigins(List.of(frontendUrl));

        // Allow common HTTP methods
        configuration.setAllowedMethods(Arrays.asList(
            "GET",
            "POST",
            "PUT",
            "DELETE",
            "OPTIONS"
        ));

        // Allow common headers
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "Accept",
            "Origin",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers"
        ));

        // Expose headers that frontend can read
        configuration.setExposedHeaders(Arrays.asList(
            "Access-Control-Allow-Origin",
            "Access-Control-Allow-Credentials"
        ));

        // CRITICAL: Allow credentials (cookies, authorization headers, etc.)
        // This is required for session-based authentication
        configuration.setAllowCredentials(true);

        // Set max age for preflight requests (1 hour)
        configuration.setMaxAge(3600L);

        // Apply CORS configuration to all endpoints
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
