package com.ecommerce.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "products")
@Data
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(length = 160) private String name;
    @Column(length = 2000) private String description;
    private Double price;
    private Integer stock;
    @Column(length = 1000) private String imageUrl;
    @Column(length = 80, unique = true) private String sku;
    @Column(length = 100) private String category;
    @Column(length = 120) private String brand;
    @Column(length = 120) private String material;
    private Boolean active = true;
}
