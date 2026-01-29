package dev.mashni.habitsapi.service;

import dev.mashni.habitsapi.payment.PaymentRepository;
import dev.mashni.habitsapi.payment.PaymentService;
import dev.mashni.habitsapi.payment.gateway.ChargeRequest;
import dev.mashni.habitsapi.payment.gateway.ChargeResponse;
import dev.mashni.habitsapi.payment.gateway.PaymentGateway;
import dev.mashni.habitsapi.payment.model.Payment;
import dev.mashni.habitsapi.payment.model.PaymentStatus;
import dev.mashni.habitsapi.payment.model.PlanPrice;
import dev.mashni.habitsapi.payment.webhook.WebhookProcessor;
import dev.mashni.habitsapi.payment.webhook.WebhookResult;
import dev.mashni.habitsapi.shared.exception.ResourceNotFoundException;
import dev.mashni.habitsapi.user.User;
import dev.mashni.habitsapi.user.UserPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private WebhookProcessor webhookProcessor;

    @InjectMocks
    private PaymentService paymentService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User("test@example.com", "Test User", "google-123");
        testUser.setId(UUID.randomUUID());
        testUser.setUserPlan(UserPlan.FREE);
    }

    @Nested
    @DisplayName("createPayment Tests")
    class CreatePaymentTests {

        @Test
        @DisplayName("Should create payment with correct amount for PRO_MONTHLY")
        void createPayment_ProMonthly_CorrectAmount() {
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });

            when(paymentGateway.createCharge(any(ChargeRequest.class))).thenReturn(
                    new ChargeResponse("corr-123", 990, "00020126...", "https://qr.example.com/corr-123", "raw")
            );

            var response = paymentService.createPayment(testUser, PlanPrice.PRO_MONTHLY);

            assertThat(response.amountInCents()).isEqualTo(990);
            assertThat(response.planDescription()).isEqualTo("Plano PRO Mensal");
            assertThat(response.qrCodeImage()).contains("qr.example.com");

            verify(paymentRepository, times(2)).save(any(Payment.class));
        }

        @Test
        @DisplayName("Should call PaymentGateway with correct parameters")
        void createPayment_CallsGatewayCorrectly() {
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });

            when(paymentGateway.createCharge(any(ChargeRequest.class))).thenReturn(
                    new ChargeResponse("corr-123", 990, "00020126...", "https://qr.example.com/corr-123", null)
            );

            paymentService.createPayment(testUser, PlanPrice.PRO_MONTHLY);

            ArgumentCaptor<ChargeRequest> requestCaptor = ArgumentCaptor.forClass(ChargeRequest.class);
            verify(paymentGateway).createCharge(requestCaptor.capture());
            ChargeRequest request = requestCaptor.getValue();
            assertThat(request.amountInCents()).isEqualTo(990);
            assertThat(request.description()).contains("Bonsaai");
        }
    }

    @Nested
    @DisplayName("getPaymentStatus Tests")
    class GetPaymentStatusTests {

        @Test
        @DisplayName("Should return payment status when payment exists and belongs to user")
        void getPaymentStatus_PaymentExistsAndBelongsToUser_ReturnsStatus() {
            UUID paymentId = UUID.randomUUID();
            UUID userId = testUser.getId();
            Payment payment = new Payment(testUser, "corr-123", 990, PlanPrice.PRO_MONTHLY);
            payment.setId(paymentId);
            when(paymentRepository.findByIdAndUserId(paymentId, userId)).thenReturn(Optional.of(payment));

            var response = paymentService.getPaymentStatus(paymentId, userId);

            assertThat(response.paymentId()).isEqualTo(paymentId);
            assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when payment not found")
        void getPaymentStatus_PaymentNotFound_ThrowsException() {
            UUID paymentId = UUID.randomUUID();
            UUID userId = testUser.getId();
            when(paymentRepository.findByIdAndUserId(paymentId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPaymentStatus(paymentId, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Payment not found");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when payment exists but belongs to different user (IDOR protection)")
        void getPaymentStatus_PaymentBelongsToDifferentUser_ThrowsException() {
            UUID paymentId = UUID.randomUUID();
            UUID differentUserId = UUID.randomUUID();
            // findByIdAndUserId returns empty when user doesn't match
            when(paymentRepository.findByIdAndUserId(paymentId, differentUserId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPaymentStatus(paymentId, differentUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Payment not found");
        }
    }

    @Nested
    @DisplayName("processWebhook Tests")
    class ProcessWebhookTests {

        @Test
        @DisplayName("Should delegate webhook processing")
        void processWebhook_DelegatesToProcessor() {
            when(webhookProcessor.process("corr-1", 990, "COMPLETED", "evt-1", "OPENPIX:CHARGE_COMPLETED"))
                    .thenReturn(WebhookResult.SUCCESS);

            var result = paymentService.processWebhook(
                    "corr-1",
                    990,
                    "COMPLETED",
                    "evt-1",
                    "OPENPIX:CHARGE_COMPLETED"
            );

            assertThat(result).isEqualTo(WebhookResult.SUCCESS);
            verify(webhookProcessor).process("corr-1", 990, "COMPLETED", "evt-1", "OPENPIX:CHARGE_COMPLETED");
        }
    }
}