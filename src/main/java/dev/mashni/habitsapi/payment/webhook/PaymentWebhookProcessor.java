package dev.mashni.habitsapi.payment.webhook;

import dev.mashni.habitsapi.payment.PaymentRepository;
import dev.mashni.habitsapi.payment.ProcessedWebhookEventRepository;
import dev.mashni.habitsapi.payment.model.Payment;
import dev.mashni.habitsapi.payment.model.PaymentStatus;
import dev.mashni.habitsapi.payment.model.ProcessedWebhookEvent;
import dev.mashni.habitsapi.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PaymentWebhookProcessor implements WebhookProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentWebhookProcessor.class);

    private final PaymentRepository paymentRepository;
    private final ProcessedWebhookEventRepository processedWebhookEventRepository;
    private final UserService userService;
    private final PaymentCompletionPolicy completionPolicy;

    public PaymentWebhookProcessor(
            PaymentRepository paymentRepository,
            ProcessedWebhookEventRepository processedWebhookEventRepository,
            UserService userService,
            PaymentCompletionPolicy completionPolicy) {
        this.paymentRepository = paymentRepository;
        this.processedWebhookEventRepository = processedWebhookEventRepository;
        this.userService = userService;
        this.completionPolicy = completionPolicy;
    }

    @Override
    @Transactional
    public WebhookResult process(
            String correlationId,
            int receivedValue,
            String chargeStatus,
            String eventId,
            String eventType) {

        if (processedWebhookEventRepository.existsByEventId(eventId)) {
            return WebhookResult.ALREADY_PROCESSED;
        }

        Optional<Payment> optPayment = paymentRepository.findByCorrelationId(correlationId);
        if (optPayment.isEmpty()) {
            return WebhookResult.PAYMENT_NOT_FOUND;
        }

        Payment payment = optPayment.get();
        WebhookResult validationResult = completionPolicy.validate(payment, receivedValue, chargeStatus);
        if (validationResult != WebhookResult.SUCCESS) {
            return validationResult;
        }

        payment.setStatus(PaymentStatus.COMPLETED);
        paymentRepository.save(payment);

        userService.upgradeToPro(payment.getUser(), payment.getPlanType().getDurationInDays());

        try {
            processedWebhookEventRepository.save(
                    new ProcessedWebhookEvent(eventId, correlationId, eventType));
        } catch (DataIntegrityViolationException e) {
            logger.info("Concurrent webhook processing detected for eventId={}", eventId);
        }

        logger.info("Payment {} completed for user {}", payment.getId(), payment.getUser().getId());
        return WebhookResult.SUCCESS;
    }
}
