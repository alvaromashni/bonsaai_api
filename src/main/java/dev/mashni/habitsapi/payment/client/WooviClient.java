package dev.mashni.habitsapi.payment.client;

import dev.mashni.habitsapi.payment.config.WooviProperties;
import dev.mashni.habitsapi.payment.dto.WooviChargeRequest;
import dev.mashni.habitsapi.payment.dto.WooviChargeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WooviClient {

    private static final Logger logger = LoggerFactory.getLogger(WooviClient.class);
    private static final String CHARGE_ENDPOINT = "/api/v1/charge";

    private final RestClient restClient;
    private final WooviProperties wooviProperties;

    public WooviClient(WooviProperties wooviProperties) {
        this.wooviProperties = wooviProperties;
        this.restClient = RestClient.builder()
            .baseUrl(wooviProperties.apiUrl())
            .defaultHeader("Authorization", wooviProperties.appId())
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    public WooviChargeResponse createCharge(WooviChargeRequest request) {
        logger.info("Creating charge with correlationID: {}", request.correlationID());

        return restClient.post()
            .uri(CHARGE_ENDPOINT)
            .body(request)
            .retrieve()
            .body(WooviChargeResponse.class);
    }
}
