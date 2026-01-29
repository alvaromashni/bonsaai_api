package dev.mashni.habitsapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mashni.habitsapi.payment.PaymentRepository;
import dev.mashni.habitsapi.payment.ProcessedWebhookEventRepository;
import dev.mashni.habitsapi.payment.dto.WooviWebhookPayload;
import dev.mashni.habitsapi.payment.model.Payment;
import dev.mashni.habitsapi.payment.model.PaymentStatus;
import dev.mashni.habitsapi.payment.model.PlanPrice;
import dev.mashni.habitsapi.user.User;
import dev.mashni.habitsapi.user.UserPlan;
import dev.mashni.habitsapi.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("WooviWebhookController Integration Tests")
class WooviWebhookControllerIntegrationTest {

    private static final String VALID_SECRET = "test-webhook-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ProcessedWebhookEventRepository processedWebhookEventRepository;

    private User testUser;
    private Payment pendingPayment;

    @BeforeEach
    void setUp() {
        processedWebhookEventRepository.deleteAll();
        paymentRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User("webhook-test@example.com", "Webhook Test User", "google-webhook-123");
        testUser.setUserPlan(UserPlan.FREE);
        testUser = userRepository.save(testUser);

        pendingPayment = new Payment(testUser, "test-correlation-id", 990, PlanPrice.PRO_MONTHLY);
        pendingPayment.setStatus(PaymentStatus.PENDING);
        pendingPayment = paymentRepository.save(pendingPayment);
    }

    @Nested
    @DisplayName("Webhook Secret Validation")
    class SecretValidationTests {

        @Test
        @DisplayName("Should reject webhook without secret header with 401")
        void webhook_NoSecret_Returns401() throws Exception {
            var payload = new WooviWebhookPayload(
                "OPENPIX:CHARGE_COMPLETED",
                new WooviWebhookPayload.Charge(pendingPayment.getCorrelationId(), 990, "COMPLETED"),
                "pix-tx-1"
            );

            mockMvc.perform(post("/api/webhooks/woovi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized());

            // Payment should remain PENDING
            Payment payment = paymentRepository.findById(pendingPayment.getId()).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("Should reject webhook with wrong secret with 401")
        void webhook_WrongSecret_Returns401() throws Exception {
            var payload = new WooviWebhookPayload(
                "OPENPIX:CHARGE_COMPLETED",
                new WooviWebhookPayload.Charge(pendingPayment.getCorrelationId(), 990, "COMPLETED"),
                "pix-tx-1"
            );

            mockMvc.perform(post("/api/webhooks/woovi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-webhook-secret", "wrong-secret")
                    .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized());

            Payment payment = paymentRepository.findById(pendingPayment.getId()).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("Valid Webhook Processing")
    class ValidWebhookTests {

        @Test
        @DisplayName("Should process CHARGE_COMPLETED and upgrade user to PRO")
        void webhook_ChargeCompleted_UpgradesUser() throws Exception {
            var payload = new WooviWebhookPayload(
                "OPENPIX:CHARGE_COMPLETED",
                new WooviWebhookPayload.Charge(pendingPayment.getCorrelationId(), 990, "COMPLETED"),
                "pix-tx-1"
            );

            mockMvc.perform(post("/api/webhooks/woovi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-webhook-secret", VALID_SECRET)
                    .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

            Payment updatedPayment = paymentRepository.findById(pendingPayment.getId()).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

            User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
            assertThat(updatedUser.getUserPlan()).isEqualTo(UserPlan.PRO);
        }

        @Test
        @DisplayName("Should be idempotent - ignore duplicate webhooks")
        void webhook_DuplicateWebhook_Idempotent() throws Exception {
            var payload = new WooviWebhookPayload(
                "OPENPIX:CHARGE_COMPLETED",
                new WooviWebhookPayload.Charge(pendingPayment.getCorrelationId(), 990, "COMPLETED"),
                "pix-tx-1"
            );

            // First call
            mockMvc.perform(post("/api/webhooks/woovi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-webhook-secret", VALID_SECRET)
                    .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

            // Second call (duplicate)
            mockMvc.perform(post("/api/webhooks/woovi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-webhook-secret", VALID_SECRET)
                    .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

            Payment payment = paymentRepository.findById(pendingPayment.getId()).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("Should ignore non-CHARGE_COMPLETED events")
        void webhook_OtherEvent_Ignored() throws Exception {
            var payload = new WooviWebhookPayload(
                "OPENPIX:CHARGE_CREATED",
                new WooviWebhookPayload.Charge(pendingPayment.getCorrelationId(), 990, "ACTIVE"),
                null
            );

            mockMvc.perform(post("/api/webhooks/woovi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-webhook-secret", VALID_SECRET)
                    .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

            Payment payment = paymentRepository.findById(pendingPayment.getId()).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("Should return 200 when payment not found (prevent retries)")
        void webhook_PaymentNotFound_Returns200() throws Exception {
            var payload = new WooviWebhookPayload(
                "OPENPIX:CHARGE_COMPLETED",
                new WooviWebhookPayload.Charge("non-existent-correlation-id", 990, "COMPLETED"),
                "pix-tx-2"
            );

            mockMvc.perform(post("/api/webhooks/woovi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-webhook-secret", VALID_SECRET)
                    .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should return 400 when payload is missing charge")
        void webhook_MissingCharge_Returns400() throws Exception {
            var payload = new WooviWebhookPayload(
                "OPENPIX:CHARGE_COMPLETED",
                null,
                null
            );

            mockMvc.perform(post("/api/webhooks/woovi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-webhook-secret", VALID_SECRET)
                    .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Content Validation")
    class ContentValidationTests {

        @Test
        @DisplayName("Should NOT upgrade when amount mismatches")
        void webhook_AmountMismatch_NoUpgrade() throws Exception {
            var payload = new WooviWebhookPayload(
                "OPENPIX:CHARGE_COMPLETED",
                new WooviWebhookPayload.Charge(pendingPayment.getCorrelationId(), 500, "COMPLETED"),
                "pix-tx-3"
            );

            mockMvc.perform(post("/api/webhooks/woovi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-webhook-secret", VALID_SECRET)
                    .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

            Payment payment = paymentRepository.findById(pendingPayment.getId()).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);

            User user = userRepository.findById(testUser.getId()).orElseThrow();
            assertThat(user.getUserPlan()).isEqualTo(UserPlan.FREE);
        }

        @Test
        @DisplayName("Should NOT upgrade when charge status is invalid")
        void webhook_InvalidChargeStatus_NoUpgrade() throws Exception {
            var payload = new WooviWebhookPayload(
                "OPENPIX:CHARGE_COMPLETED",
                new WooviWebhookPayload.Charge(pendingPayment.getCorrelationId(), 990, "ACTIVE"),
                "pix-tx-4"
            );

            mockMvc.perform(post("/api/webhooks/woovi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-webhook-secret", VALID_SECRET)
                    .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

            Payment payment = paymentRepository.findById(pendingPayment.getId()).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("Should process PRO_QUARTERLY webhook correctly")
        void webhook_ProQuarterly_UpgradesCorrectly() throws Exception {
            Payment quarterlyPayment = new Payment(testUser, "quarterly-corr-id", 2590, PlanPrice.PRO_QUARTERLY);
            quarterlyPayment.setStatus(PaymentStatus.PENDING);
            quarterlyPayment = paymentRepository.save(quarterlyPayment);

            var payload = new WooviWebhookPayload(
                "OPENPIX:CHARGE_COMPLETED",
                new WooviWebhookPayload.Charge(quarterlyPayment.getCorrelationId(), 2590, "COMPLETED"),
                "pix-tx-5"
            );

            mockMvc.perform(post("/api/webhooks/woovi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-webhook-secret", VALID_SECRET)
                    .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

            Payment updatedPayment = paymentRepository.findById(quarterlyPayment.getId()).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }
    }
}
