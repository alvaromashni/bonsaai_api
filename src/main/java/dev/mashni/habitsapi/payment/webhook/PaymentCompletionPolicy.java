package dev.mashni.habitsapi.payment.webhook;

import dev.mashni.habitsapi.payment.model.Payment;

public interface PaymentCompletionPolicy {
    WebhookResult validate(Payment payment, int receivedValue, String chargeStatus);
}
