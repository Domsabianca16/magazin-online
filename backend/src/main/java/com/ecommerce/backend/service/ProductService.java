package com.ecommerce.backend.service;

import com.ecommerce.backend.entity.Product;
import com.ecommerce.backend.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service // Marchează această clasă ca fiind un Service administrat de Spring
public class ProductService {

    private final ProductRepository productRepository;

    // Injectăm Repository-ul în Service
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // Metodă pentru a aduce toate produsele
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    // Metodă pentru a salva un produs
    public Product saveProduct(Product product) {
        return productRepository.save(product);
    }
}