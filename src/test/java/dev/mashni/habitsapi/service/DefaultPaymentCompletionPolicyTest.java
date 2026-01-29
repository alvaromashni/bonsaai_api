package dev.mashni.habitsapi.service;

import dev.mashni.habitsapi.payment.model.Payment;
import dev.mashni.habitsapi.payment.model.PaymentStatus;
import dev.mashni.habitsapi.payment.model.PlanPrice;
import dev.mashni.habitsapi.payment.webhook.DefaultPaymentCompletionPolicy;
import dev.mashni.habitsapi.payment.webhook.WebhookResult;
import dev.mashni.habitsapi.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DefaultPaymentCompletionPolicy Unit Tests")
class DefaultPaymentCompletionPolicyTest {

    private final DefaultPaymentCompletionPolicy policy = new DefaultPaymentCompletionPolicy();

    @Test
    @DisplayName("Should accept COMPLETED status for pending payment")
    void validate_CompletedStatus_Success() {
        Payment payment = pendingPayment();

        WebhookResult result = policy.validate(payment, 990, "COMPLETED");

        assertThat(result).isEqualTo(WebhookResult.SUCCESS);
    }

    @Test
    @DisplayName("Should reject non-completion status")
    void validate_InvalidStatus() {
        Payment payment = pendingPayment();

        WebhookResult result = policy.validate(payment, 990, "ACTIVE");

        assertThat(result).isEqualTo(WebhookResult.INVALID_STATUS);
    }

    @Test
    @DisplayName("Should reject amount mismatch")
    void validate_AmountMismatch() {
        Payment payment = pendingPayment();

        WebhookResult result = policy.validate(payment, 500, "COMPLETED");

        assertThat(result).isEqualTo(WebhookResult.AMOUNT_MISMATCH);
    }

    @Test
    @DisplayName("Should reject non-pending payments")
    void validate_NotPending() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.COMPLETED);

        WebhookResult result = policy.validate(payment, 990, "COMPLETED");

        assertThat(result).isEqualTo(WebhookResult.NOT_PENDING);
    }

    private Payment pendingPayment() {
        User user = new User("test@example.com", "Test User", "google-123");
        user.setId(UUID.randomUUID());
        Payment payment = new Payment(user, "corr-123", 990, PlanPrice.PRO_MONTHLY);
        payment.setStatus(PaymentStatus.PENDING);
        return payment;
    }
}