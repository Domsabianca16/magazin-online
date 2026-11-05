package com.ecommerce.backend.config;

import com.ecommerce.backend.entity.Product;
import com.ecommerce.backend.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("dev")
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(ProductRepository repository) {
        return args -> {
            // Verificăm dacă baza de date e goală, ca să nu duplicăm produsele la fiecare restart
            if (repository.count() == 0) {
                Product p1 = new Product();
                p1.setName("Laptop Gaming");
                p1.setDescription("Laptop performant pentru jocuri și programare");
                p1.setPrice(4500.0);
                p1.setStock(10);
                p1.setImageUrl("https://images.unsplash.com/photo-1517336714731-489689fd1ca8");
                repository.save(p1);

                Product p2 = new Product();
                p2.setName("Telefon Inteligent");
                p2.setDescription("Smartphone de ultimă generație cu cameră performantă");
                p2.setPrice(2500.0);
                p2.setStock(15);
                p2.setImageUrl("https://images.unsplash.com/photo-1511707171634-5f897ff02aa9");
                repository.save(p2);

                System.out.println("Produsele de test au fost adăugate în baza de date!");
            }
        };
    }
}
