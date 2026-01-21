package dev.mashni.habitsapi.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WooviChargeResponse(
    Charge charge
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Charge(
        String correlationID,
        int value,
        String status,
        String brCode,
        String qrCodeImage,
        String paymentLinkUrl
    ) {}
}
