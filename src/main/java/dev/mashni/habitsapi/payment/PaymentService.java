package dev.mashni.habitsapi.payment;

import dev.mashni.habitsapi.payment.dto.CheckoutResponse;
import dev.mashni.habitsapi.payment.dto.PaymentStatusResponse;
import dev.mashni.habitsapi.payment.gateway.ChargeRequest;
import dev.mashni.habitsapi.payment.gateway.ChargeResponse;
import dev.mashni.habitsapi.payment.gateway.PaymentGateway;
import dev.mashni.habitsapi.payment.model.Payment;
import dev.mashni.habitsapi.payment.model.PlanPrice;
import dev.mashni.habitsapi.payment.webhook.WebhookProcessor;
import dev.mashni.habitsapi.payment.webhook.WebhookResult;
import dev.mashni.habitsapi.shared.exception.ResourceNotFoundException;
import dev.mashni.habitsapi.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final WebhookProcessor webhookProcessor;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentGateway paymentGateway,
            WebhookProcessor webhookProcessor) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.webhookProcessor = webhookProcessor;
    }

    @Transactional
    public CheckoutResponse createPayment(User user, PlanPrice planType) {
        logger.info("Creating payment for user {} with plan {}", user.getId(), planType);

        String correlationId = UUID.randomUUID().toString();
        int amountInCents = planType.getPriceInCents();

        Payment payment = new Payment(user, correlationId, amountInCents, planType);
        payment = paymentRepository.save(payment);

        ChargeRequest chargeRequest = new ChargeRequest(
                correlationId,
                amountInCents,
                "Bonsaai - " + planType.getDescription()
        );

        ChargeResponse response = paymentGateway.createCharge(chargeRequest);

        if (response.rawResponse() != null) {
            payment.setRawResponse(response.rawResponse());
            paymentRepository.save(payment);
        }

        return new CheckoutResponse(
            payment.getId(),
            response.qrCodeImage(),
            response.brCode(),
            amountInCents,
            planType.getDescription()
        );
    }

    public PaymentStatusResponse getPaymentStatus(UUID paymentId, UUID userId) {
        Payment payment = paymentRepository.findByIdAndUserId(paymentId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        return new PaymentStatusResponse(
            payment.getId(),
            payment.getStatus(),
            payment.getPlanType().getDescription()
        );
    }

    @Transactional
    public WebhookResult processWebhook(
            String correlationId,
            int receivedValue,
            String chargeStatus,
            String eventId,
            String eventType) {
        return webhookProcessor.process(correlationId, receivedValue, chargeStatus, eventId, eventType);
    }
}
