package dev.mashni.habitsapi.payment.gateway;

public record ChargeRequest(
        String correlationId,
        int amountInCents,
        String description
) {
}
