package com.ecommerce.backend.service;

import com.ecommerce.backend.entity.Product;
import com.ecommerce.backend.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class ProductService {
    private final ProductRepository productRepository;
    public ProductService(ProductRepository productRepository) { this.productRepository = productRepository; }
    public List<Product> getAllProducts() { return productRepository.findAll(); }
    public Product saveProduct(Product product) { validate(product); product.setId(null); return productRepository.save(product); }
    public Product updateProduct(Long id, Product input) {
        validate(input);
        Product product = productRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        product.setName(input.getName().trim()); product.setDescription(input.getDescription()); product.setPrice(input.getPrice()); product.setStock(input.getStock()); product.setImageUrl(input.getImageUrl());
        return productRepository.save(product);
    }
    public void deleteProduct(Long id) { if (!productRepository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"); productRepository.deleteById(id); }
    private void validate(Product product) {
        if (product == null || product.getName() == null || product.getName().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product name is required");
        if (product.getPrice() == null || !Double.isFinite(product.getPrice()) || product.getPrice() < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price must be zero or higher");
        if (product.getStock() == null || product.getStock() < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock must be zero or higher");
        if (product.getName().length() > 160) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product name is too long"); product.setName(product.getName().trim());
    }
}
