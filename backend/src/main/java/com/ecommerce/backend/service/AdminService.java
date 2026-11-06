package com.ecommerce.backend.service;

import com.ecommerce.backend.entity.Product;
import com.ecommerce.backend.entity.StoreOrder;
import com.ecommerce.backend.entity.StoreOrderItem;
import com.ecommerce.backend.repository.ProductRepository;
import com.ecommerce.backend.repository.StoreOrderRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service
public class AdminService {
    private final ProductRepository products;
    private final StoreOrderRepository orders;
    public AdminService(ProductRepository products, StoreOrderRepository orders) { this.products=products; this.orders=orders; }

    public record ItemView(Long productId, String name, int quantity, long unitPriceCents) {}
    public record OrderView(Long id, String status, String paymentStatus, String paymentMethod, String customerName, String email, String phone, String address, String city, String postalCode, long subtotalCents, long shippingCents, long totalCents, Instant createdAt, Instant refundedAt, String refundNote, List<ItemView> items) {}
    public record CustomerView(String name, String email, String phone, long orderCount, long orderValueCents, Instant lastOrderAt) {}
    private static class CustomerAggregate { String name; String email; String phone; long count; long value; Instant last; }

    public List<Product> getProducts() { return products.findAll(Sort.by(Sort.Direction.DESC,"id")); }
    @Transactional public List<OrderView> getOrders() { return orders.findAll(Sort.by(Sort.Direction.DESC,"createdAt")).stream().map(this::view).toList(); }
    @Transactional public List<CustomerView> getCustomers() {
        Map<String,CustomerAggregate> buyers = new HashMap<>();
        orders.findAll(Sort.by(Sort.Direction.DESC,"createdAt")).forEach(order -> {
            String key=order.getEmail().trim().toLowerCase(Locale.ROOT); CustomerAggregate buyer=buyers.computeIfAbsent(key,k->new CustomerAggregate());
            if(buyer.name==null){buyer.name=order.getCustomerName();buyer.email=order.getEmail();buyer.phone=order.getPhone();buyer.last=order.getCreatedAt();}
            buyer.count++; buyer.value+=order.getTotalCents();
        });
        return buyers.values().stream().sorted(Comparator.comparingLong((CustomerAggregate c)->c.count).reversed()).map(c->new CustomerView(c.name,c.email,c.phone,c.count,c.value,c.last)).toList();
    }
    @Transactional public OrderView updateOrderStatus(Long id,String rawStatus) {
        if(rawStatus==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Status is required");
        String status=rawStatus.trim().toUpperCase(Locale.ROOT);
        if(!Set.of("PENDING","PROCESSING","SHIPPED","DELIVERED","CANCELLED").contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported order status");
        StoreOrder order=order(id); order.setStatus(status); return view(orders.save(order));
    }
    @Transactional public OrderView markPaid(Long id) {
        StoreOrder order=order(id);
        if(!"CASH_ON_DELIVERY".equals(order.getPaymentMethod()) || !"DELIVERED".equals(order.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Mark as collected only after a cash-on-delivery order is delivered");
        if(!"UNPAID".equals(order.getPaymentStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Payment is already recorded");
        order.setPaymentStatus("PAID"); return view(orders.save(order));
    }
    @Transactional public OrderView recordManualRefund(Long id,String note) {
        StoreOrder order=order(id);
        if(!"PAID".equals(order.getPaymentStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Record a refund only after payment has been collected");
        if(!"CASH_ON_DELIVERY".equals(order.getPaymentMethod())) throw new ResponseStatusException(HttpStatus.CONFLICT,"This store has no online payment provider configured");
        order.setPaymentStatus("REFUNDED"); order.setRefundedAt(Instant.now());
        if(note!=null && !note.isBlank()) order.setRefundNote(note.trim().substring(0,Math.min(500,note.trim().length())));
        return view(orders.save(order));
    }
    private StoreOrder order(Long id){return orders.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));}
    private OrderView view(StoreOrder o){return new OrderView(o.getId(),o.getStatus(),o.getPaymentStatus(),o.getPaymentMethod(),o.getCustomerName(),o.getEmail(),o.getPhone(),o.getAddress(),o.getCity(),o.getPostalCode(),o.getSubtotalCents(),o.getShippingCents(),o.getTotalCents(),o.getCreatedAt(),o.getRefundedAt(),o.getRefundNote(),o.getItems().stream().map(i->new ItemView(i.getProductId(),i.getProductName(),i.getQuantity(),i.getUnitPriceCents())).toList());}
}
