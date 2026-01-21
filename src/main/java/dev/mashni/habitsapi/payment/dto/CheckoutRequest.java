package dev.mashni.habitsapi.payment.dto;

import dev.mashni.habitsapi.payment.model.PlanPrice;
import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(
    @NotNull(message = "Plan type is required")
    PlanPrice planType
) {}
