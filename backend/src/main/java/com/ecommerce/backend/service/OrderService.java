package com.ecommerce.backend.service;

import com.ecommerce.backend.entity.Product;
import com.ecommerce.backend.entity.ProductVariant;
import com.ecommerce.backend.entity.StoreOrder;
import com.ecommerce.backend.entity.StoreOrderItem;
import com.ecommerce.backend.repository.ProductRepository;
import com.ecommerce.backend.repository.StoreOrderRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Service
public class OrderService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final ProductRepository products;
    private final StoreOrderRepository orders;

    public OrderService(ProductRepository products, StoreOrderRepository orders) {
        this.products = products; this.orders = orders;
    }

    public record ItemRequest(Long productId, Integer quantity, String variantName) {
        public ItemRequest(Long productId, Integer quantity) { this(productId, quantity, null); }
    }
    public record CreateOrderRequest(String name, String email, String phone, String address,
                                     String city, String postal, String paymentMethod, java.util.List<ItemRequest> items) {}
    public record ItemResult(Long productId, String name, String variantName, String variantColor, int quantity, long unitPriceCents) {}
    public record OrderResult(Long id, String status, String paymentMethod, long subtotalCents,
                              long shippingCents, long totalCents, java.time.Instant createdAt,
                              java.util.List<ItemResult> items) {}

    @Transactional
    public OrderResult create(String idempotencyKey, CreateOrderRequest request) {
        return create(idempotencyKey, request, false);
    }

    @Transactional
    public OrderResult createStripeOrder(String idempotencyKey, CreateOrderRequest request) {
        return create(idempotencyKey, request, true);
    }

    private OrderResult create(String idempotencyKey, CreateOrderRequest request, boolean stripe) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 80)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid Idempotency-Key header is required");
        var prior = orders.findByIdempotencyKey(idempotencyKey);
        if (prior.isPresent()) {
            StoreOrder previous = prior.get();
            if (stripe && "STRIPE".equals(previous.getPaymentMethod()) && "FAILED".equals(previous.getPaymentStatus()))
                return reopenStripeOrder(previous);
            return result(previous);
        }
        validate(request, stripe);

        Map<Long, Integer> quantities = new TreeMap<>();
        for (ItemRequest item : request.items()) {
            if (item == null || item.productId() == null || item.quantity() == null || item.quantity() < 1 || item.quantity() > 99)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product quantities must be between 1 and 99");
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }

        StoreOrder order = new StoreOrder();
        order.setIdempotencyKey(idempotencyKey);
        order.setCustomerName(clean(request.name(), 120));
        order.setEmail(request.email().trim().toLowerCase());
        order.setPhone(clean(request.phone(), 40));
        order.setAddress(clean(request.address(), 240));
        order.setCity(clean(request.city(), 100));
        order.setPostalCode(clean(request.postal(), 20));
        order.setPaymentMethod(stripe ? "STRIPE" : "CASH_ON_DELIVERY");
        if (stripe) { order.setPaymentStatus("PENDING"); order.setStatus("PAYMENT_PENDING"); }
        Map<Long, Product> lockedProducts = new TreeMap<>();
        for (var line : quantities.entrySet()) {
            if (line.getValue() > 99) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum quantity per product is 99");
            Product product = products.findByIdForUpdate(line.getKey())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "A product in the bag is no longer available"));
            if (product.getPrice() == null || product.getPrice() < 0 || product.getStock() == null || product.getStock() < line.getValue())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock for " + product.getName());
            product.setStock(product.getStock() - line.getValue());
            lockedProducts.put(product.getId(), product);
        }
        long subtotal = 0;
        for (ItemRequest line : request.items()) {
            Product product = lockedProducts.get(line.productId());
            ProductVariant variant = null;
            if (line.variantName() != null && !line.variantName().isBlank()) {
                variant = product.getVariants().stream().filter(v -> v.getName().equalsIgnoreCase(line.variantName().trim())).findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "A selected product variant is no longer available"));
            } else if (!product.getVariants().isEmpty()) {
                variant = product.getVariants().get(0);
            }
            long unitCents = BigDecimal.valueOf(product.getPrice()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
            subtotal = Math.addExact(subtotal, Math.multiplyExact(unitCents, line.quantity()));
            order.getItems().add(new StoreOrderItem(order, product.getId(), product.getName(), unitCents, line.quantity(),
                    variant == null ? null : variant.getName(), variant == null ? null : variant.getColorHex()));
        }
        long shipping = subtotal == 0 || subtotal >= 30_000 ? 0 : 1_900;
        order.setSubtotalCents(subtotal);
        order.setShippingCents(shipping);
        order.setTotalCents(Math.addExact(subtotal, shipping));
        if (stripe && order.getTotalCents() < 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plata cu cardul este disponibilă pentru comenzi de cel puțin 2 lei");
        return result(orders.saveAndFlush(order));
    }

    private OrderResult reopenStripeOrder(StoreOrder order) {
        Map<Long, Integer> quantities = new TreeMap<>();
        for (StoreOrderItem item : order.getItems()) quantities.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        for (var line : quantities.entrySet()) {
            Product product = products.findByIdForUpdate(line.getKey())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "A product in the bag is no longer available"));
            if (product.getStock() == null || product.getStock() < line.getValue())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock for " + product.getName());
            product.setStock(product.getStock() - line.getValue());
        }
        order.setPaymentStatus("PENDING"); order.setStatus("PAYMENT_PENDING");
        order.setStripeSessionId(null); order.setStripeCheckoutUrl(null);
        return result(orders.saveAndFlush(order));
    }

    @Transactional
    public void markStripePaid(String sessionId) {
        StoreOrder order = orders.findByStripeSessionId(sessionId).orElse(null);
        if (order == null) return;
        if ("STRIPE".equals(order.getPaymentMethod()) && "PENDING".equals(order.getPaymentStatus())) {
            order.setPaymentStatus("PAID"); order.setStatus("PENDING"); orders.save(order);
        }
    }

    @Transactional
    public void markStripeExpired(String sessionId) {
        StoreOrder order = orders.findByStripeSessionId(sessionId).orElse(null);
        if (order == null) return;
        if (!"STRIPE".equals(order.getPaymentMethod()) || !"PENDING".equals(order.getPaymentStatus())) return;
        for (StoreOrderItem item : order.getItems()) {
            products.findByIdForUpdate(item.getProductId()).ifPresent(product -> product.setStock((product.getStock() == null ? 0 : product.getStock()) + item.getQuantity()));
        }
        order.setPaymentStatus("FAILED"); order.setStatus("CANCELLED"); orders.save(order);
    }

    @Transactional
    public OrderResult saveStripeSession(Long orderId, String sessionId, String url) {
        StoreOrder order = orders.findById(orderId).orElseThrow();
        order.setStripeSessionId(sessionId); order.setStripeCheckoutUrl(url);
        return result(orders.save(order));
    }

    @Transactional
    public PaymentStatus paymentStatus(String sessionId) {
        StoreOrder order = orders.findByStripeSessionId(sessionId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment session not found"));
        return new PaymentStatus(order.getId(), order.getPaymentStatus(), order.getTotalCents());
    }
    public record PaymentStatus(Long orderId, String paymentStatus, long totalCents) {}

    private static void validate(CreateOrderRequest r, boolean stripe) {
        if (r == null || blank(r.name()) || blank(r.email()) || !EMAIL.matcher(r.email().trim()).matches()
                || blank(r.phone()) || blank(r.address()) || blank(r.city()) || blank(r.postal())
                || r.items() == null || r.items().isEmpty() || r.items().size() > 50)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complete all delivery details and add at least one product");
        if (!stripe && r.paymentMethod() != null && !r.paymentMethod().equals("CASH_ON_DELIVERY"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment method");
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String clean(String value, int max) {
        String cleaned = value.trim().replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ");
        if (cleaned.isBlank() || cleaned.length() > max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A delivery field is empty or too long");
        return cleaned;
    }
    private static OrderResult result(StoreOrder order) {
        return new OrderResult(order.getId(), order.getStatus(), order.getPaymentMethod(), order.getSubtotalCents(),
                order.getShippingCents(), order.getTotalCents(), order.getCreatedAt(),
                order.getItems().stream().map(i -> new ItemResult(i.getProductId(), i.getProductName(), i.getVariantName(), i.getVariantColor(), i.getQuantity(), i.getUnitPriceCents())).toList());
    }
}
