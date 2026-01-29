package dev.mashni.habitsapi.payment.gateway;

public record ChargeResponse(
        String correlationId,
        int amountInCents,
        String brCode,
        String qrCodeImage,
        String rawResponse
) {
}
