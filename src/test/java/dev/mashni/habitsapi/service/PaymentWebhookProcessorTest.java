package dev.mashni.habitsapi.service;

import dev.mashni.habitsapi.payment.PaymentRepository;
import dev.mashni.habitsapi.payment.ProcessedWebhookEventRepository;
import dev.mashni.habitsapi.payment.model.Payment;
import dev.mashni.habitsapi.payment.model.PaymentStatus;
import dev.mashni.habitsapi.payment.model.PlanPrice;
import dev.mashni.habitsapi.payment.webhook.PaymentCompletionPolicy;
import dev.mashni.habitsapi.payment.webhook.PaymentWebhookProcessor;
import dev.mashni.habitsapi.payment.webhook.WebhookResult;
import dev.mashni.habitsapi.user.User;
import dev.mashni.habitsapi.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentWebhookProcessor Unit Tests")
class PaymentWebhookProcessorTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ProcessedWebhookEventRepository processedWebhookEventRepository;

    @Mock
    private UserService userService;

    @Mock
    private PaymentCompletionPolicy completionPolicy;

    @InjectMocks
    private PaymentWebhookProcessor processor;

    private Payment payment;

    @BeforeEach
    void setUp() {
        User user = new User("test@example.com", "Test User", "google-123");
        user.setId(UUID.randomUUID());

        payment = new Payment(user, "corr-123", 990, PlanPrice.PRO_MONTHLY);
        payment.setId(UUID.randomUUID());
        payment.setStatus(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("Should return ALREADY_PROCESSED for duplicate event")
    void processWebhook_DuplicateEvent_AlreadyProcessed() {
        when(processedWebhookEventRepository.existsByEventId("evt-1")).thenReturn(true);

        var result = processor.process("corr-123", 990, "COMPLETED", "evt-1", "OPENPIX:CHARGE_COMPLETED");

        assertThat(result).isEqualTo(WebhookResult.ALREADY_PROCESSED);
        verifyNoInteractions(paymentRepository, userService, completionPolicy);
    }

    @Test
    @DisplayName("Should return PAYMENT_NOT_FOUND when correlationId is unknown")
    void processWebhook_PaymentNotFound() {
        when(processedWebhookEventRepository.existsByEventId("evt-1")).thenReturn(false);
        when(paymentRepository.findByCorrelationId("invalid")).thenReturn(Optional.empty());

        var result = processor.process("invalid", 990, "COMPLETED", "evt-1", "OPENPIX:CHARGE_COMPLETED");

        assertThat(result).isEqualTo(WebhookResult.PAYMENT_NOT_FOUND);
        verifyNoInteractions(userService, completionPolicy);
    }

    @Test
    @DisplayName("Should return validation result when policy rejects")
    void processWebhook_PolicyRejects_ReturnsReason() {
        when(processedWebhookEventRepository.existsByEventId("evt-1")).thenReturn(false);
        when(paymentRepository.findByCorrelationId("corr-123")).thenReturn(Optional.of(payment));
        when(completionPolicy.validate(payment, 990, "ACTIVE")).thenReturn(WebhookResult.INVALID_STATUS);

        var result = processor.process("corr-123", 990, "ACTIVE", "evt-1", "OPENPIX:CHARGE_COMPLETED");

        assertThat(result).isEqualTo(WebhookResult.INVALID_STATUS);
        verify(userService, never()).upgradeToPro(any(), anyInt());
        verify(processedWebhookEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should confirm payment and upgrade user on success")
    void processWebhook_Success_UpgradesUser() {
        when(processedWebhookEventRepository.existsByEventId("evt-1")).thenReturn(false);
        when(paymentRepository.findByCorrelationId("corr-123")).thenReturn(Optional.of(payment));
        when(completionPolicy.validate(payment, 990, "COMPLETED")).thenReturn(WebhookResult.SUCCESS);

        var result = processor.process("corr-123", 990, "COMPLETED", "evt-1", "OPENPIX:CHARGE_COMPLETED");

        assertThat(result).isEqualTo(WebhookResult.SUCCESS);
        verify(paymentRepository).save(payment);
        verify(userService).upgradeToPro(payment.getUser(), 30);
        verify(processedWebhookEventRepository).save(any());
    }
}