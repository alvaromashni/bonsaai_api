package dev.mashni.habitsapi.payment.webhook;

public enum WebhookResult {
    SUCCESS,
    ALREADY_PROCESSED,
    PAYMENT_NOT_FOUND,
    INVALID_STATUS,
    AMOUNT_MISMATCH,
    NOT_PENDING
}
