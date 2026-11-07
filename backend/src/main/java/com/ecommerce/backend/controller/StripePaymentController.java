package com.ecommerce.backend.controller;

import com.ecommerce.backend.service.OrderService;
import com.ecommerce.backend.service.StripePaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments/stripe")
public class StripePaymentController {
    private final StripePaymentService stripe;
    public StripePaymentController(StripePaymentService stripe) { this.stripe = stripe; }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public StripePaymentService.CheckoutResult createCheckout(
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody OrderService.CreateOrderRequest request) {
        return stripe.createCheckout(key, request);
    }

    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.OK)
    public void webhook(@RequestBody String payload,
                        @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        stripe.handleWebhook(payload, signature);
    }

    @GetMapping("/session/{sessionId}")
    public OrderService.PaymentStatus paymentStatus(@PathVariable String sessionId) {
        return stripe.getPaymentStatus(sessionId);
    }
}
