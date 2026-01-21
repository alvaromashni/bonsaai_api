package dev.mashni.habitsapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mashni.habitsapi.payment.PaymentRepository;
import dev.mashni.habitsapi.payment.client.WooviClient;
import dev.mashni.habitsapi.payment.dto.CheckoutRequest;
import dev.mashni.habitsapi.payment.dto.WooviChargeRequest;
import dev.mashni.habitsapi.payment.dto.WooviChargeResponse;
import dev.mashni.habitsapi.payment.model.Payment;
import dev.mashni.habitsapi.payment.model.PaymentStatus;
import dev.mashni.habitsapi.payment.model.PlanPrice;
import dev.mashni.habitsapi.user.User;
import dev.mashni.habitsapi.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("PaymentController Integration Tests")
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockBean
    private WooviClient wooviClient;

    private User testUser;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User("test@example.com", "Test User", "google-test-123");
        testUser = userRepository.save(testUser);

        // Mock WooviClient to return a valid response
        when(wooviClient.createCharge(any(WooviChargeRequest.class)))
            .thenAnswer(inv -> {
                WooviChargeRequest req = inv.getArgument(0);
                return new WooviChargeResponse(
                    new WooviChargeResponse.Charge(
                        req.correlationID(),
                        req.value(),
                        "ACTIVE",
                        "00020126580014br.gov.bcb.pix...",
                        "https://api.openpix.com.br/qrcode/" + req.correlationID(),
                        "https://openpix.com.br/pay/" + req.correlationID()
                    )
                );
            });
    }

    private OAuth2User createOAuth2User(User user) {
        return new DefaultOAuth2User(
            java.util.Collections.emptyList(),
            Map.of(
                "sub", user.getGoogleId(),
                "email", user.getEmail(),
                "name", user.getName()
            ),
            "sub"
        );
    }

    @Nested
    @DisplayName("POST /api/payments/checkout")
    class CheckoutTests {

        @Test
        @DisplayName("Should create checkout successfully for PRO_MONTHLY")
        void checkout_ProMonthly_Success() throws Exception {
            var request = new CheckoutRequest(PlanPrice.PRO_MONTHLY);

            mockMvc.perform(post("/api/payments/checkout")
                    .with(oauth2Login().oauth2User(createOAuth2User(testUser)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").exists())
                .andExpect(jsonPath("$.qrCodeImage").exists())
                .andExpect(jsonPath("$.brCode").exists())
                .andExpect(jsonPath("$.amountInCents").value(990))
                .andExpect(jsonPath("$.planDescription").value("Plano PRO Mensal"));

            // Verify payment was persisted
            var payments = paymentRepository.findAll();
            assertThat(payments).hasSize(1);
            assertThat(payments.get(0).getAmountInCents()).isEqualTo(990);
            assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payments.get(0).getUser().getId()).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("Should create checkout successfully for PRO_QUARTERLY")
        void checkout_ProQuarterly_Success() throws Exception {
            var request = new CheckoutRequest(PlanPrice.PRO_QUARTERLY);

            mockMvc.perform(post("/api/payments/checkout")
                    .with(oauth2Login().oauth2User(createOAuth2User(testUser)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountInCents").value(2590))
                .andExpect(jsonPath("$.planDescription").value("Plano PRO Trimestral"));
        }

        @Test
        @DisplayName("Should create checkout successfully for PRO_YEARLY")
        void checkout_ProYearly_Success() throws Exception {
            var request = new CheckoutRequest(PlanPrice.PRO_YEARLY);

            mockMvc.perform(post("/api/payments/checkout")
                    .with(oauth2Login().oauth2User(createOAuth2User(testUser)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountInCents").value(9999))
                .andExpect(jsonPath("$.planDescription").value("Plano PRO Anual"));
        }

        @Test
        @DisplayName("Should return 400 when planType is missing")
        void checkout_MissingPlanType_Returns400() throws Exception {
            mockMvc.perform(post("/api/payments/checkout")
                    .with(oauth2Login().oauth2User(createOAuth2User(testUser)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 302 redirect when not authenticated")
        void checkout_NotAuthenticated_ReturnsRedirect() throws Exception {
            var request = new CheckoutRequest(PlanPrice.PRO_MONTHLY);

            mockMvc.perform(post("/api/payments/checkout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isFound()); // 302 redirect to login
        }
    }

    @Nested
    @DisplayName("GET /api/payments/{paymentId}/status")
    class GetPaymentStatusTests {

        @Test
        @DisplayName("Should return payment status when payment exists")
        void getStatus_PaymentExists_ReturnsStatus() throws Exception {
            // Create a payment first
            Payment payment = new Payment(testUser, UUID.randomUUID().toString(), 990, PlanPrice.PRO_MONTHLY);
            payment = paymentRepository.save(payment);

            mockMvc.perform(get("/api/payments/{paymentId}/status", payment.getId())
                    .with(oauth2Login().oauth2User(createOAuth2User(testUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(payment.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.planDescription").value("Plano PRO Mensal"));
        }

        @Test
        @DisplayName("Should return COMPLETED status after payment is processed")
        void getStatus_CompletedPayment_ReturnsCompleted() throws Exception {
            // Create and complete a payment
            Payment payment = new Payment(testUser, UUID.randomUUID().toString(), 990, PlanPrice.PRO_MONTHLY);
            payment.setStatus(PaymentStatus.COMPLETED);
            payment = paymentRepository.save(payment);

            mockMvc.perform(get("/api/payments/{paymentId}/status", payment.getId())
                    .with(oauth2Login().oauth2User(createOAuth2User(testUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("Should return 404 when payment not found")
        void getStatus_PaymentNotFound_Returns404() throws Exception {
            UUID randomId = UUID.randomUUID();

            mockMvc.perform(get("/api/payments/{paymentId}/status", randomId)
                    .with(oauth2Login().oauth2User(createOAuth2User(testUser))))
                .andExpect(status().isNotFound());
        }
    }
}
