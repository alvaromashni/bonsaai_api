package dev.mashni.habitsapi.payment;

import dev.mashni.habitsapi.payment.dto.WooviWebhookPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks")
public class WooviWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(WooviWebhookController.class);
    private static final String CHARGE_COMPLETED_EVENT = "OPENPIX:CHARGE_COMPLETED";

    private final PaymentService paymentService;

    public WooviWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/woovi")
    public ResponseEntity<Void> handleWebhook(@RequestBody WooviWebhookPayload payload) {
        logger.info("Received webhook event: {}", payload.event());

        if (!CHARGE_COMPLETED_EVENT.equals(payload.event())) {
            logger.info("Ignoring event: {}", payload.event());
            return ResponseEntity.ok().build();
        }

        if (payload.charge() == null || payload.charge().correlationID() == null) {
            logger.warn("Webhook payload missing charge or correlationID");
            return ResponseEntity.badRequest().build();
        }

        try {
            paymentService.processWebhook(payload.charge().correlationID());
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to process webhook: {}", e.getMessage());
            // Return 200 to prevent retries for non-existent payments
            return ResponseEntity.ok().build();
        }

        return ResponseEntity.ok().build();
    }
}
