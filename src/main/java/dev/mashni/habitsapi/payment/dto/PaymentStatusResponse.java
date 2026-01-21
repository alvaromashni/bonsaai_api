package dev.mashni.habitsapi.payment.dto;

import dev.mashni.habitsapi.payment.model.PaymentStatus;

import java.util.UUID;

public record PaymentStatusResponse(
    UUID paymentId,
    PaymentStatus status,
    String planDescription
) {}
