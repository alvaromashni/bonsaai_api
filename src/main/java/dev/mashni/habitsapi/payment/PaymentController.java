package dev.mashni.habitsapi.payment;

import dev.mashni.habitsapi.payment.dto.CheckoutRequest;
import dev.mashni.habitsapi.payment.dto.CheckoutResponse;
import dev.mashni.habitsapi.payment.dto.PaymentStatusResponse;
import dev.mashni.habitsapi.user.User;
import dev.mashni.habitsapi.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final UserService userService;

    public PaymentController(PaymentService paymentService, UserService userService) {
        this.paymentService = paymentService;
        this.userService = userService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(
            @Valid @RequestBody CheckoutRequest request,
            Authentication authentication) {

        User user = userService.getUserFromAuthentication(authentication);
        CheckoutResponse response = paymentService.createPayment(user, request.planType());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{paymentId}/status")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(
            @PathVariable UUID paymentId) {

        PaymentStatusResponse response = paymentService.getPaymentStatus(paymentId);
        return ResponseEntity.ok(response);
    }
}
