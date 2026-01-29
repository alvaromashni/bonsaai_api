package dev.mashni.habitsapi.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "woovi")
public record WooviProperties(
    String appId,
    String apiUrl,
    String webhookSecret
) {}