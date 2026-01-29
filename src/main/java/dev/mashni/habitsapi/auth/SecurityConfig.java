package dev.mashni.habitsapi.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomAuthenticationSuccessHandler authenticationSuccessHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(
            CustomOAuth2UserService customOAuth2UserService,
            CustomAuthenticationSuccessHandler authenticationSuccessHandler,
            CorsConfigurationSource corsConfigurationSource) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.authenticationSuccessHandler = authenticationSuccessHandler;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Use CsrfTokenRequestAttributeHandler so the frontend can read the XSRF-TOKEN
        // cookie and send it back as the X-XSRF-TOKEN header on mutating requests.
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            // CSRF enabled for all session-based /api/** routes.
            // Only webhooks are exempt (protected by webhook secret signature).
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(requestHandler)
                .ignoringRequestMatchers("/api/webhooks/**")
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/error", "/login**", "/oauth2/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/webhooks/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                )
                .successHandler(authenticationSuccessHandler)
            );

        return http.build();
    }
}
