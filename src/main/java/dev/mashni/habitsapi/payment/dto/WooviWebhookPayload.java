package dev.mashni.habitsapi.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WooviWebhookPayload(
    String event,
    Charge charge
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Charge(
        String correlationID,
        int value,
        String status
    ) {}
}
