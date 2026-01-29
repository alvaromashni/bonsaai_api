package dev.mashni.habitsapi.payment.webhook;

import dev.mashni.habitsapi.payment.model.Payment;
import dev.mashni.habitsapi.payment.model.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DefaultPaymentCompletionPolicy implements PaymentCompletionPolicy {

    private static final Set<String> VALID_COMPLETED_STATUSES = Set.of("COMPLETED", "PAID", "CONFIRMED");

    @Override
    public WebhookResult validate(Payment payment, int receivedValue, String chargeStatus) {
        if (chargeStatus == null || !VALID_COMPLETED_STATUSES.contains(chargeStatus.toUpperCase())) {
            return WebhookResult.INVALID_STATUS;
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            return WebhookResult.NOT_PENDING;
        }

        if (receivedValue != payment.getAmountInCents()) {
            return WebhookResult.AMOUNT_MISMATCH;
        }

        return WebhookResult.SUCCESS;
    }
}
