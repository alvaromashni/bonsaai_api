package dev.mashni.habitsapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mashni.habitsapi.payment.PaymentRepository;
import dev.mashni.habitsapi.payment.PaymentService;
import dev.mashni.habitsapi.payment.client.WooviClient;
import dev.mashni.habitsapi.payment.dto.WooviChargeRequest;
import dev.mashni.habitsapi.payment.dto.WooviChargeResponse;
import dev.mashni.habitsapi.payment.model.Payment;
import dev.mashni.habitsapi.payment.model.PaymentStatus;
import dev.mashni.habitsapi.payment.model.PlanPrice;
import dev.mashni.habitsapi.user.User;
import dev.mashni.habitsapi.user.UserPlan;
import dev.mashni.habitsapi.user.UserService;
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
    private WooviClient wooviClient;

    @Mock
    private UserService userService;

    @Mock
    private ObjectMapper objectMapper;

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
            // Arrange
            var wooviResponse = createWooviResponse("corr-123", 990);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(wooviClient.createCharge(any(WooviChargeRequest.class))).thenReturn(wooviResponse);

            // Act
            var response = paymentService.createPayment(testUser, PlanPrice.PRO_MONTHLY);

            // Assert
            assertThat(response.amountInCents()).isEqualTo(990);
            assertThat(response.planDescription()).isEqualTo("Plano PRO Mensal");
            assertThat(response.qrCodeImage()).isEqualTo("https://qr.example.com/corr-123");
            assertThat(response.brCode()).isEqualTo("00020126...");

            // Verify payment was saved
            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository, times(2)).save(paymentCaptor.capture());
            Payment savedPayment = paymentCaptor.getAllValues().get(0);
            assertThat(savedPayment.getAmountInCents()).isEqualTo(990);
            assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(savedPayment.getPlanType()).isEqualTo(PlanPrice.PRO_MONTHLY);
        }

        @Test
        @DisplayName("Should create payment with correct amount for PRO_QUARTERLY")
        void createPayment_ProQuarterly_CorrectAmount() {
            // Arrange
            var wooviResponse = createWooviResponse("corr-456", 2590);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(wooviClient.createCharge(any(WooviChargeRequest.class))).thenReturn(wooviResponse);

            // Act
            var response = paymentService.createPayment(testUser, PlanPrice.PRO_QUARTERLY);

            // Assert
            assertThat(response.amountInCents()).isEqualTo(2590);
            assertThat(response.planDescription()).isEqualTo("Plano PRO Trimestral");
        }

        @Test
        @DisplayName("Should create payment with correct amount for PRO_YEARLY")
        void createPayment_ProYearly_CorrectAmount() {
            // Arrange
            var wooviResponse = createWooviResponse("corr-789", 9999);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(wooviClient.createCharge(any(WooviChargeRequest.class))).thenReturn(wooviResponse);

            // Act
            var response = paymentService.createPayment(testUser, PlanPrice.PRO_YEARLY);

            // Assert
            assertThat(response.amountInCents()).isEqualTo(9999);
            assertThat(response.planDescription()).isEqualTo("Plano PRO Anual");
        }

        @Test
        @DisplayName("Should call WooviClient with correct parameters")
        void createPayment_CallsWooviClientCorrectly() {
            // Arrange
            var wooviResponse = createWooviResponse("corr-123", 990);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(wooviClient.createCharge(any(WooviChargeRequest.class))).thenReturn(wooviResponse);

            // Act
            paymentService.createPayment(testUser, PlanPrice.PRO_MONTHLY);

            // Assert
            ArgumentCaptor<WooviChargeRequest> requestCaptor = ArgumentCaptor.forClass(WooviChargeRequest.class);
            verify(wooviClient).createCharge(requestCaptor.capture());
            WooviChargeRequest request = requestCaptor.getValue();
            assertThat(request.value()).isEqualTo(990);
            assertThat(request.comment()).contains("Bonsaai");
            assertThat(request.correlationID()).isNotNull();
        }
    }

    @Nested
    @DisplayName("getPaymentStatus Tests")
    class GetPaymentStatusTests {

        @Test
        @DisplayName("Should return payment status when payment exists")
        void getPaymentStatus_PaymentExists_ReturnsStatus() {
            // Arrange
            UUID paymentId = UUID.randomUUID();
            Payment payment = new Payment(testUser, "corr-123", 990, PlanPrice.PRO_MONTHLY);
            payment.setId(paymentId);
            payment.setStatus(PaymentStatus.PENDING);
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

            // Act
            var response = paymentService.getPaymentStatus(paymentId);

            // Assert
            assertThat(response.paymentId()).isEqualTo(paymentId);
            assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
            assertThat(response.planDescription()).isEqualTo("Plano PRO Mensal");
        }

        @Test
        @DisplayName("Should throw exception when payment not found")
        void getPaymentStatus_PaymentNotFound_ThrowsException() {
            // Arrange
            UUID paymentId = UUID.randomUUID();
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> paymentService.getPaymentStatus(paymentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payment not found");
        }
    }

    @Nested
    @DisplayName("processWebhook Tests")
    class ProcessWebhookTests {

        @Test
        @DisplayName("Should update payment status and upgrade user when payment is PENDING")
        void processWebhook_PendingPayment_UpdatesStatusAndUpgradesUser() {
            // Arrange
            String correlationId = "corr-123";
            Payment payment = new Payment(testUser, correlationId, 990, PlanPrice.PRO_MONTHLY);
            payment.setId(UUID.randomUUID());
            payment.setStatus(PaymentStatus.PENDING);
            when(paymentRepository.findByCorrelationId(correlationId)).thenReturn(Optional.of(payment));

            // Act
            paymentService.processWebhook(correlationId);

            // Assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            verify(paymentRepository).save(payment);
            verify(userService).upgradeToPro(testUser, 30); // PRO_MONTHLY = 30 days
        }

        @Test
        @DisplayName("Should ignore webhook when payment is already COMPLETED (idempotency)")
        void processWebhook_AlreadyCompleted_IgnoresWebhook() {
            // Arrange
            String correlationId = "corr-123";
            Payment payment = new Payment(testUser, correlationId, 990, PlanPrice.PRO_MONTHLY);
            payment.setId(UUID.randomUUID());
            payment.setStatus(PaymentStatus.COMPLETED);
            when(paymentRepository.findByCorrelationId(correlationId)).thenReturn(Optional.of(payment));

            // Act
            paymentService.processWebhook(correlationId);

            // Assert
            verify(paymentRepository, never()).save(any());
            verify(userService, never()).upgradeToPro(any(), anyInt());
        }

        @Test
        @DisplayName("Should throw exception when payment not found by correlationId")
        void processWebhook_PaymentNotFound_ThrowsException() {
            // Arrange
            String correlationId = "invalid-corr";
            when(paymentRepository.findByCorrelationId(correlationId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> paymentService.processWebhook(correlationId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payment not found for correlationID");
        }

        @Test
        @DisplayName("Should upgrade user with correct duration for PRO_QUARTERLY")
        void processWebhook_ProQuarterly_UpgradesFor90Days() {
            // Arrange
            String correlationId = "corr-456";
            Payment payment = new Payment(testUser, correlationId, 2590, PlanPrice.PRO_QUARTERLY);
            payment.setId(UUID.randomUUID());
            payment.setStatus(PaymentStatus.PENDING);
            when(paymentRepository.findByCorrelationId(correlationId)).thenReturn(Optional.of(payment));

            // Act
            paymentService.processWebhook(correlationId);

            // Assert
            verify(userService).upgradeToPro(testUser, 90);
        }

        @Test
        @DisplayName("Should upgrade user with correct duration for PRO_YEARLY")
        void processWebhook_ProYearly_UpgradesFor365Days() {
            // Arrange
            String correlationId = "corr-789";
            Payment payment = new Payment(testUser, correlationId, 9999, PlanPrice.PRO_YEARLY);
            payment.setId(UUID.randomUUID());
            payment.setStatus(PaymentStatus.PENDING);
            when(paymentRepository.findByCorrelationId(correlationId)).thenReturn(Optional.of(payment));

            // Act
            paymentService.processWebhook(correlationId);

            // Assert
            verify(userService).upgradeToPro(testUser, 365);
        }
    }

    private WooviChargeResponse createWooviResponse(String correlationId, int value) {
        return new WooviChargeResponse(
            new WooviChargeResponse.Charge(
                correlationId,
                value,
                "ACTIVE",
                "00020126...",
                "https://qr.example.com/" + correlationId,
                "https://pay.example.com/" + correlationId
            )
        );
    }
}
