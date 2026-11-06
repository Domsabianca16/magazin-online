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
    @Column(length = 60) private String variantName;
    @Column(length = 20) private String variantColor;

    protected StoreOrderItem() {}
    public StoreOrderItem(StoreOrder order, Long productId, String productName, long unitPriceCents, int quantity) {
        this(order, productId, productName, unitPriceCents, quantity, null, null);
    }
    public StoreOrderItem(StoreOrder order, Long productId, String productName, long unitPriceCents, int quantity, String variantName, String variantColor) {
        this.order=order; this.productId=productId; this.productName=productName; this.unitPriceCents=unitPriceCents; this.quantity=quantity; this.variantName=variantName; this.variantColor=variantColor;
    }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public long getUnitPriceCents() { return unitPriceCents; }
    public int getQuantity() { return quantity; }
    public String getVariantName() { return variantName; }
    public String getVariantColor() { return variantColor; }
}
