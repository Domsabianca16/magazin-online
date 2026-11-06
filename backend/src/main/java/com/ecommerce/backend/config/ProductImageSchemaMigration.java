package com.ecommerce.backend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class ProductImageSchemaMigration {
    @Bean
    CommandLineRunner widenProductImageUrlColumn(JdbcTemplate jdbc) {
        return args -> {
            String dataType = jdbc.queryForObject(
                    "select data_type from information_schema.columns where table_schema = current_schema() and table_name = 'products' and column_name = 'image_url'",
                    String.class);
            if (!"text".equalsIgnoreCase(dataType)) {
                jdbc.execute("alter table products alter column image_url type text");
            }
        };
    }
}
