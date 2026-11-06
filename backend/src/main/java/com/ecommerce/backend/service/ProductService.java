package com.ecommerce.backend.service;

import com.ecommerce.backend.entity.Product;
import com.ecommerce.backend.entity.ProductVariant;
import com.ecommerce.backend.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.net.URI;

@Service
public class ProductService {
    private final ProductRepository productRepository;
    public ProductService(ProductRepository productRepository) { this.productRepository = productRepository; }
    public List<Product> getAllProducts() { return productRepository.findAll().stream().filter(p -> !Boolean.FALSE.equals(p.getActive())).toList(); }
    public Product saveProduct(Product product) { validate(product); product.setId(null); if(product.getActive()==null) product.setActive(true); return productRepository.save(product); }
    public Product updateProduct(Long id, Product input) {
        validate(input); Product product=productRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"));
        product.setName(input.getName().trim()); product.setDescription(input.getDescription()); product.setPrice(input.getPrice()); product.setStock(input.getStock()); product.setImageUrl(input.getImageUrl()); product.setSku(input.getSku()); product.setCategory(input.getCategory()); product.setBrand(input.getBrand()); product.setMaterial(input.getMaterial()); product.setActive(input.getActive()==null || input.getActive()); product.getVariants().clear(); product.getVariants().addAll(input.getVariants());
        return productRepository.save(product);
    }
    public void deleteProduct(Long id) { if(!productRepository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"); productRepository.deleteById(id); }
    private void validate(Product p) {
        if(p==null || p.getName()==null || p.getName().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Product name is required");
        if(p.getPrice()==null || !Double.isFinite(p.getPrice()) || p.getPrice()<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Price must be zero or higher");
        if(p.getStock()==null || p.getStock()<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Stock must be zero or higher");
        if(p.getName().trim().length()>160) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Product name is too long");
        p.setName(p.getName().trim()); if(p.getSku()!=null && p.getSku().isBlank()) p.setSku(null);
        if(p.getVariants()==null)p.setVariants(new java.util.ArrayList<>());
        if(p.getVariants().size()>12)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A product can have at most 12 variants");
        java.util.Set<String> variantNames=new java.util.HashSet<>();
        for(ProductVariant variant:p.getVariants()){
            if(variant.getName()==null||variant.getName().isBlank()||variant.getName().trim().length()>60)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Each variant needs a name of at most 60 characters");
            variant.setName(variant.getName().trim());
            if(!variantNames.add(variant.getName().toLowerCase(java.util.Locale.ROOT)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Variant names must be unique for a product");
            if(variant.getColorHex()!=null&&!variant.getColorHex().isBlank()&&!variant.getColorHex().matches("^#[0-9a-fA-F]{6}$"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Variant color must be a six-digit hex value");
            if(variant.getImageUrl()==null||variant.getImageUrl().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Each variant needs its own image URL");
            variant.setImageUrl(normalizeImageUrl(variant.getImageUrl()));
        }
        if(p.getImageUrl()!=null&&!p.getImageUrl().isBlank())p.setImageUrl(normalizeImageUrl(p.getImageUrl()));else p.setImageUrl(null);
    }
    private static String normalizeImageUrl(String value) {
        String image=value.trim(); if(image.length()>2048)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Image URL must be 2048 characters or fewer");
        try { URI uri=URI.create(image); if(uri.getHost()==null||!("http".equalsIgnoreCase(uri.getScheme())||"https".equalsIgnoreCase(uri.getScheme())))throw new IllegalArgumentException(); return uri.toASCIIString(); }
        catch(IllegalArgumentException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Image URL must be a valid http or https link");}
    }
}
