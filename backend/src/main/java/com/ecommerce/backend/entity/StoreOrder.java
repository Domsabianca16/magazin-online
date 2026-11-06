package com.ecommerce.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "store_orders")
public class StoreOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 80)
    private String idempotencyKey;
    @Column(nullable = false, length = 120) private String customerName;
    @Column(nullable = false, length = 254) private String email;
    @Column(nullable = false, length = 40) private String phone;
    @Column(nullable = false, length = 240) private String address;
    @Column(nullable = false, length = 100) private String city;
    @Column(nullable = false, length = 20) private String postalCode;
    @Column(nullable = false, length = 40) private String paymentMethod = "CASH_ON_DELIVERY";
    @Column(nullable = false, length = 30) private String status = "PENDING";
    @Column(length = 30) private String paymentStatus = "UNPAID";
    private Instant refundedAt;
    @Column(length = 500) private String refundNote;
    @Column(nullable = false) private long subtotalCents;
    @Column(nullable = false) private long shippingCents;
    @Column(nullable = false) private long totalCents;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StoreOrderItem> items = new ArrayList<>();

    public Long getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String v) { idempotencyKey = v; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String v) { customerName = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { email = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { phone = v; }
    public String getAddress() { return address; }
    public void setAddress(String v) { address = v; }
    public String getCity() { return city; }
    public void setCity(String v) { city = v; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String v) { postalCode = v; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public String getPaymentStatus() { return paymentStatus == null ? "UNPAID" : paymentStatus; }
    public void setPaymentStatus(String v) { paymentStatus = v; }
    public Instant getRefundedAt() { return refundedAt; }
    public void setRefundedAt(Instant v) { refundedAt = v; }
    public String getRefundNote() { return refundNote; }
    public void setRefundNote(String v) { refundNote = v; }
    public long getSubtotalCents() { return subtotalCents; }
    public void setSubtotalCents(long v) { subtotalCents = v; }
    public long getShippingCents() { return shippingCents; }
    public void setShippingCents(long v) { shippingCents = v; }
    public long getTotalCents() { return totalCents; }
    public void setTotalCents(long v) { totalCents = v; }
    public Instant getCreatedAt() { return createdAt; }
    public List<StoreOrderItem> getItems() { return items; }
}
