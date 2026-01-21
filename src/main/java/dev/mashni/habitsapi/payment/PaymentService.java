package dev.mashni.habitsapi.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mashni.habitsapi.payment.client.WooviClient;
import dev.mashni.habitsapi.payment.dto.CheckoutResponse;
import dev.mashni.habitsapi.payment.dto.PaymentStatusResponse;
import dev.mashni.habitsapi.payment.dto.WooviChargeRequest;
import dev.mashni.habitsapi.payment.dto.WooviChargeResponse;
import dev.mashni.habitsapi.payment.model.Payment;
import dev.mashni.habitsapi.payment.model.PaymentStatus;
import dev.mashni.habitsapi.payment.model.PlanPrice;
import dev.mashni.habitsapi.user.User;
import dev.mashni.habitsapi.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final WooviClient wooviClient;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public PaymentService(
            PaymentRepository paymentRepository,
            WooviClient wooviClient,
            UserService userService,
            ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.wooviClient = wooviClient;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CheckoutResponse createPayment(User user, PlanPrice planType) {
        logger.info("Creating payment for user {} with plan {}", user.getId(), planType);

        String correlationId = UUID.randomUUID().toString();
        int amountInCents = planType.getPriceInCents();

        // Save payment as PENDING
        Payment payment = new Payment(user, correlationId, amountInCents, planType);
        payment = paymentRepository.save(payment);

        // Create charge on Woovi
        WooviChargeRequest chargeRequest = new WooviChargeRequest(
            correlationId,
            amountInCents,
            "Bonsaai - " + planType.getDescription()
        );

        WooviChargeResponse response = wooviClient.createCharge(chargeRequest);

        // Save raw response for auditing
        try {
            payment.setRawResponse(objectMapper.writeValueAsString(response));
            paymentRepository.save(payment);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize Woovi response for payment {}", payment.getId(), e);
        }

        return new CheckoutResponse(
            payment.getId(),
            response.charge().qrCodeImage(),
            response.charge().brCode(),
            amountInCents,
            planType.getDescription()
        );
    }

    public PaymentStatusResponse getPaymentStatus(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        return new PaymentStatusResponse(
            payment.getId(),
            payment.getStatus(),
            payment.getPlanType().getDescription()
        );
    }

    @Transactional
    public void processWebhook(String correlationId) {
        logger.info("Processing webhook for correlationID: {}", correlationId);

        Payment payment = paymentRepository.findByCorrelationId(correlationId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found for correlationID: " + correlationId));

        // Idempotency check
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            logger.info("Payment {} already completed, ignoring webhook", payment.getId());
            return;
        }

        // Update payment status
        payment.setStatus(PaymentStatus.COMPLETED);
        paymentRepository.save(payment);

        // Upgrade user to PRO
        userService.upgradeToPro(payment.getUser(), payment.getPlanType().getDurationInDays());

        logger.info("Payment {} completed successfully for user {}", payment.getId(), payment.getUser().getId());
    }
}
