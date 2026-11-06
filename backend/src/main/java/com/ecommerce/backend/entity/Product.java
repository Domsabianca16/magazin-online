package com.ecommerce.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Data
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(length = 160) private String name;
    @Column(length = 2000) private String description;
    private Double price;
    private Integer stock;
    @Column(columnDefinition = "TEXT") private String imageUrl;
    @Column(length = 80, unique = true) private String sku;
    @Column(length = 100) private String category;
    @Column(length = 120) private String brand;
    @Column(length = 120) private String material;
    private Boolean active = true;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_variants", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "display_order")
    private List<ProductVariant> variants = new ArrayList<>();
}
