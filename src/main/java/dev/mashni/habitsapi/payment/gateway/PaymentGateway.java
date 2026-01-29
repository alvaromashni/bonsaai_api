package dev.mashni.habitsapi.payment.gateway;

public interface PaymentGateway {
    ChargeResponse createCharge(ChargeRequest request);
}
