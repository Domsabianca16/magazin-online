package com.ecommerce.backend.service;

import com.ecommerce.backend.entity.StoreOrder;
import com.ecommerce.backend.repository.StoreOrderRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StripePaymentService {
    private final OrderService orders;
    private final StoreOrderRepository orderRepository;
    @Value("${STRIPE_SECRET_KEY:}") private String secretKey;
    @Value("${STRIPE_WEBHOOK_SECRET:}") private String webhookSecret;
    @Value("${FRONTEND_URL:https://magazin-online-eosin.vercel.app}") private String frontendUrl;

    public StripePaymentService(OrderService orders, StoreOrderRepository orderRepository) {
        this.orders = orders; this.orderRepository = orderRepository;
    }

    public CheckoutResult createCheckout(String idempotencyKey, OrderService.CreateOrderRequest request) {
        requireSecret();
        OrderService.OrderResult order = orders.createStripeOrder(idempotencyKey, request);
        StoreOrder persisted = orderRepository.findById(order.id()).orElseThrow();
        try {
            RequestOptions stripeOptions = RequestOptions.builder().setApiKey(secretKey).build();
            if ("PAID".equals(persisted.getPaymentStatus()))
                return new CheckoutResult(order.id(), null, true, order.totalCents());
            if (persisted.getStripeSessionId() != null && "PENDING".equals(persisted.getPaymentStatus())) {
                Session previous = Session.retrieve(persisted.getStripeSessionId(), stripeOptions);
                if ("paid".equals(previous.getPaymentStatus())) {
                    orders.markStripePaid(previous.getId());
                    return new CheckoutResult(order.id(), null, true, order.totalCents());
                }
                if ("open".equals(previous.getStatus()))
                    return new CheckoutResult(order.id(), previous.getUrl(), false, order.totalCents());
            }
            SessionCreateParams.Builder params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(frontendOrigin() + "/?payment=success&session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(frontendOrigin() + "/?payment=cancelled&order_id=" + order.id())
                    .setCustomerEmail(request.email().trim())
                    .setClientReferenceId(order.id().toString())
                    .putMetadata("order_id", order.id().toString())
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD);
            for (OrderService.ItemResult item : order.items()) {
                params.addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity((long) item.quantity())
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("ron").setUnitAmount(item.unitPriceCents())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder().setName(item.name() + (item.variantName() == null ? "" : " - " + item.variantName())).build())
                                .build()).build());
            }
            if (order.shippingCents() > 0) {
                params.addLineItem(SessionCreateParams.LineItem.builder().setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder().setCurrency("ron")
                                .setUnitAmount(order.shippingCents())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder().setName("Livrare").build()).build()).build());
            }
            RequestOptions options = RequestOptions.builder().setApiKey(secretKey)
                    .setIdempotencyKey("fest-iq-order-" + order.id() + "-" + java.util.UUID.randomUUID()).build();
            Session session = Session.create(params.build(), options);
            orders.saveStripeSession(order.id(), session.getId(), session.getUrl());
            return new CheckoutResult(order.id(), session.getUrl(), false, order.totalCents());
        } catch (StripeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Nu am putut inițializa plata cu Stripe. Încearcă din nou.", e);
        }
    }

    public void handleWebhook(String payload, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe webhook is not configured");
        if (signature == null || signature.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe signature is required");
        try {
            Event event = Webhook.constructEvent(payload, signature, webhookSecret);
            StripeObject object = event.getDataObjectDeserializer().getObject().orElse(null);
            if (!(object instanceof Session session)) return;
            switch (event.getType()) {
                case "checkout.session.completed" -> {
                    if ("paid".equals(session.getPaymentStatus())) orders.markStripePaid(session.getId());
                }
                case "checkout.session.expired" -> orders.markStripeExpired(session.getId());
                default -> { }
            }
        } catch (com.stripe.exception.SignatureVerificationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Stripe webhook signature");
        }
    }

    public OrderService.PaymentStatus getPaymentStatus(String sessionId) {
        return orders.paymentStatus(sessionId);
    }

    private void requireSecret() {
        if (secretKey == null || secretKey.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Plata cu cardul nu este configurată. Adaugă STRIPE_SECRET_KEY în backend.");
        if (webhookSecret == null || webhookSecret.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Plata cu cardul nu este complet configurată. Adaugă STRIPE_WEBHOOK_SECRET în backend.");
    }
    private String frontendOrigin() { return frontendUrl == null ? "http://localhost:5173" : frontendUrl.trim().replaceAll("/+$", ""); }
    public record CheckoutResult(Long orderId, String checkoutUrl, boolean alreadyPaid, long totalCents) {}
}
