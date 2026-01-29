package dev.mashni.habitsapi.payment.webhook;

public interface WebhookProcessor {
    WebhookResult process(String correlationId, int receivedValue, String chargeStatus, String eventId, String eventType);
}
