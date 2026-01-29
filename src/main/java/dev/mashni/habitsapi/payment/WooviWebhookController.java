package dev.mashni.habitsapi.payment;

import dev.mashni.habitsapi.payment.config.WooviProperties;
import dev.mashni.habitsapi.payment.dto.WooviWebhookPayload;
import dev.mashni.habitsapi.payment.webhook.WebhookResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
public class WooviWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(WooviWebhookController.class);
    private static final String CHARGE_COMPLETED_EVENT = "OPENPIX:CHARGE_COMPLETED";

    private final PaymentService paymentService;
    private final WooviProperties wooviProperties;

    public WooviWebhookController(PaymentService paymentService, WooviProperties wooviProperties) {
        this.paymentService = paymentService;
        this.wooviProperties = wooviProperties;
    }

    @PostMapping("/woovi")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody WooviWebhookPayload payload,
            @RequestHeader(value = "x-webhook-secret", required = false) String webhookSecret) {

        // 1. Validate webhook secret
        if (!isValidSecret(webhookSecret)) {
            logger.warn("Webhook rejected: invalid or missing secret. event={}, correlationId={}",
                    payload.event(),
                    payload.charge() != null ? payload.charge().correlationID() : "null");
            return ResponseEntity.status(401).build();
        }

        logger.info("Webhook received: event={}, correlationId={}",
                payload.event(),
                payload.charge() != null ? payload.charge().correlationID() : "null");

        // 2. Only process CHARGE_COMPLETED events
        if (!CHARGE_COMPLETED_EVENT.equals(payload.event())) {
            logger.info("Webhook ignored: unsupported event={}", payload.event());
            return ResponseEntity.ok().build();
        }

        // 3. Validate payload structure
        if (payload.charge() == null || payload.charge().correlationID() == null) {
            logger.warn("Webhook rejected: missing charge or correlationID");
            return ResponseEntity.badRequest().build();
        }

        // 4. Derive a unique event ID for idempotency
        String eventId = deriveEventId(payload);

        // 5. Process the webhook
        try {
            WebhookResult result = paymentService.processWebhook(
                    payload.charge().correlationID(),
                    payload.charge().value(),
                    payload.charge().status(),
                    eventId,
                    payload.event()
            );

            switch (result) {
                case ALREADY_PROCESSED -> logger.info("Webhook idempotent: eventId={}, correlationId={}",
                        eventId, payload.charge().correlationID());
                case PAYMENT_NOT_FOUND -> logger.warn("Webhook rejected: payment not found. correlationId={}",
                        payload.charge().correlationID());
                case INVALID_STATUS -> logger.warn("Webhook rejected: invalid charge status={}. correlationId={}",
                        payload.charge().status(), payload.charge().correlationID());
                case AMOUNT_MISMATCH -> logger.warn("Webhook rejected: amount mismatch. correlationId={}, receivedValue={}",
                        payload.charge().correlationID(), payload.charge().value());
                case NOT_PENDING -> logger.info("Webhook ignored: payment not pending. correlationId={}",
                        payload.charge().correlationID());
                case SUCCESS -> logger.info("Webhook accepted: payment confirmed. correlationId={}",
                        payload.charge().correlationID());
            }
        } catch (Exception e) {
            logger.error("Webhook processing error: correlationId={}, error={}",
                    payload.charge().correlationID(), e.getMessage(), e);
        }

        // Always return 200 to prevent retries from the provider
        return ResponseEntity.ok().build();
    }

    private boolean isValidSecret(String providedSecret) {
        String expectedSecret = wooviProperties.webhookSecret();
        if (expectedSecret == null || expectedSecret.isBlank()) {
            logger.error("Webhook secret not configured! All webhooks will be rejected.");
            return false;
        }
        return expectedSecret.equals(providedSecret);
    }

    private String deriveEventId(WooviWebhookPayload payload) {
        if (payload.pixTransactionID() != null && !payload.pixTransactionID().isBlank()) {
            return payload.pixTransactionID();
        }
        // Fallback: use correlationID + event as composite key
        return payload.charge().correlationID() + ":" + payload.event();
    }
}
