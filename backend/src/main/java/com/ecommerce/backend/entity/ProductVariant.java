package com.ecommerce.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ProductVariant {
    @Column(name = "variant_name", nullable = false, length = 60) private String name;
    @Column(name = "color_hex", length = 20) private String colorHex;
    @Column(name = "image_url", columnDefinition = "TEXT") private String imageUrl;
    public ProductVariant() {}
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
