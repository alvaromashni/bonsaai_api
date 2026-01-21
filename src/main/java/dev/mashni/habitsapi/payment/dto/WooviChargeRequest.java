package dev.mashni.habitsapi.payment.dto;

public record WooviChargeRequest(
    String correlationID,
    int value,
    String comment
) {}
