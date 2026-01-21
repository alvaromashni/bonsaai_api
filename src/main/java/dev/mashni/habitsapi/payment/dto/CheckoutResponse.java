package dev.mashni.habitsapi.payment.dto;

import java.util.UUID;

public record CheckoutResponse(
    UUID paymentId,
    String qrCodeImage,
    String brCode,
    int amountInCents,
    String planDescription
) {}
