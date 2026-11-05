package com.ecommerce.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "store_order_items")
public class StoreOrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private StoreOrder order;
    @Column(nullable = false) private Long productId;
    @Column(nullable = false, length = 200) private String productName;
    @Column(nullable = false) private long unitPriceCents;
    @Column(nullable = false) private int quantity;

    protected StoreOrderItem() {}
    public StoreOrderItem(StoreOrder order, Long productId, String productName, long unitPriceCents, int quantity) {
        this.order = order; this.productId = productId; this.productName = productName;
        this.unitPriceCents = unitPriceCents; this.quantity = quantity;
    }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public long getUnitPriceCents() { return unitPriceCents; }
    public int getQuantity() { return quantity; }
}
