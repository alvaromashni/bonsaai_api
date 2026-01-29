package dev.mashni.habitsapi.payment.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mashni.habitsapi.payment.client.WooviClient;
import dev.mashni.habitsapi.payment.dto.WooviChargeRequest;
import dev.mashni.habitsapi.payment.dto.WooviChargeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WooviPaymentGateway implements PaymentGateway {

    private static final Logger logger = LoggerFactory.getLogger(WooviPaymentGateway.class);

    private final WooviClient wooviClient;
    private final ObjectMapper objectMapper;

    public WooviPaymentGateway(WooviClient wooviClient, ObjectMapper objectMapper) {
        this.wooviClient = wooviClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChargeResponse createCharge(ChargeRequest request) {
        WooviChargeRequest wooviRequest = new WooviChargeRequest(
                request.correlationId(),
                request.amountInCents(),
                request.description()
        );

        WooviChargeResponse response = wooviClient.createCharge(wooviRequest);

        String rawResponse = null;
        try {
            rawResponse = objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize Woovi response for correlationId {}", request.correlationId(), e);
        }

        return new ChargeResponse(
                response.charge().correlationID(),
                response.charge().value(),
                response.charge().brCode(),
                response.charge().qrCodeImage(),
                rawResponse
        );
    }
}
