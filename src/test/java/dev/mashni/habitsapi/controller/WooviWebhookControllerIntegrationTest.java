package dev.mashni.habitsapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mashni.habitsapi.payment.PaymentRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("WooviWebhookController Integration Tests")
class WooviWebhookControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private User testUser;
    private Payment pendingPayment;

    @BeforeEach
    void setUp() {
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
    @DisplayName("POST /api/webhooks/woovi")
    class WebhookTests {

        @Test
        @DisplayName("Should process CHARGE_COMPLETED and upgrade user to PRO")
        void webhook_ChargeCompleted_UpgradesUser() throws Exception {
            var payload = new WooviWebhookPayload(
                "OPENPIX:CHARGE_COMPLETED",
                new WooviWebhookPayload.Charge(
                    pendingPayment.getCorrelationId(),
                    990,
                    "COMPLETED"
                )
            );

            mockMvc.perform(post("/api/webhooks/woovi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

            // Verify payment status was updated
            Payment updatedPayment = paymentRepository.findById(pendingPayment.getId()).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

            // Verify user was upgraded to PRO
            User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
            assertThat(updatedUser.getUserPlan()).isEqualTo(UserPlan.PRO);
        }

        @Test
        @DisplayName("Should be idempotent - ignore duplicate webhooks")
        void webhook_DuplicateWebhook_Idempotent() throws Exception {
            // First webhook
            var payload = new WooviWebhookPayload(
                "OPENPIX:CHARGE_COMPLETED",
                new WooviWebhookPayload.Charge(
                    pendingPayment.getCorrelationId(),
                    990,
                    "COMPLETED"
                )
            );

            mockMvc.perform(post("/api/webhooks/woovi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

            // Second webhook (duplicate)
            mockMvc.perform(post("/api/webhooks/woovi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

            // Should still have only one completed payment
            Payment payment = paymentRepository.findById(pendingPayment.getId()).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("Should ignore non-CHARGE_COMPLETED events")
        void webhook_OtherEvent_Ignored() throws Exception {
            var payload = new WooviWebhookPayload(
                "OPENPIX:CHARGE_CREATED",
                new WooviWebhookPayload.Charge(
                    pendingPayment.getCorrelationId(),
                    990,
                    "ACTIVE"
                )
            );

            mockMvc.perform(post("/api/webhooks/woovi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

            // Payment should still be PENDING
            Payment payment = paymentRepository.findById(pendingPayment.getId()).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);

            // User should still be FREE
            User user = userRepository.findById(testUser.getId()).orElseThrow();
            assertThat(user.getUserPlan()).isEqualTo(UserPlan.FREE);
        }

        @Test
        @DisplayName("Should return 200 even when payment not found (prevent retries)")
        void webhook_PaymentNotFound_Returns200() throws Exception {
            var payload = new WooviWebhookPayload(
                "OPENPIX:CHARGE_COMPLETED",
                new WooviWebhookPayload.Charge(
                    "non-existent-correlation-id",
                    990,
                    "COMPLETED"
                )
            );

            mockMvc.perform(post("/api/webhooks/woovi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should return 400 when payload is missing charge")
        void webhook_MissingCharge_Returns400() throws Exception {
            var payload = new WooviWebhookPayload(
                "OPENPIX:CHARGE_COMPLETED",
                null
            );

            mockMvc.perform(post("/api/webhooks/woovi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should be accessible without authentication (public endpoint)")
        void webhook_NoAuthentication_StillAccessible() throws Exception {
            var payload = new WooviWebhookPayload(
                "OPENPIX:CHARGE_COMPLETED",
                new WooviWebhookPayload.Charge(
                    pendingPayment.getCorrelationId(),
                    990,
                    "COMPLETED"
                )
            );

            // No oauth2Login() - simulating external webhook call
            mockMvc.perform(post("/api/webhooks/woovi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should process webhook for PRO_QUARTERLY plan")
        void webhook_ProQuarterly_UpgradesCorrectly() throws Exception {
            // Create a quarterly payment
            Payment quarterlyPayment = new Payment(testUser, "quarterly-corr-id", 2590, PlanPrice.PRO_QUARTERLY);
            quarterlyPayment.setStatus(PaymentStatus.PENDING);
            quarterlyPayment = paymentRepository.save(quarterlyPayment);

            var payload = new WooviWebhookPayload(
                "OPENPIX:CHARGE_COMPLETED",
                new WooviWebhookPayload.Charge(
                    quarterlyPayment.getCorrelationId(),
                    2590,
                    "COMPLETED"
                )
            );

            mockMvc.perform(post("/api/webhooks/woovi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

            // Verify payment was completed
            Payment updatedPayment = paymentRepository.findById(quarterlyPayment.getId()).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("Should process webhook for PRO_YEARLY plan")
        void webhook_ProYearly_UpgradesCorrectly() throws Exception {
            // Create a yearly payment
            Payment yearlyPayment = new Payment(testUser, "yearly-corr-id", 9999, PlanPrice.PRO_YEARLY);
            yearlyPayment.setStatus(PaymentStatus.PENDING);
            yearlyPayment = paymentRepository.save(yearlyPayment);

            var payload = new WooviWebhookPayload(
                "OPENPIX:CHARGE_COMPLETED",
                new WooviWebhookPayload.Charge(
                    yearlyPayment.getCorrelationId(),
                    9999,
                    "COMPLETED"
                )
            );

            mockMvc.perform(post("/api/webhooks/woovi")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

            // Verify payment was completed
            Payment updatedPayment = paymentRepository.findById(yearlyPayment.getId()).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }
    }
}
