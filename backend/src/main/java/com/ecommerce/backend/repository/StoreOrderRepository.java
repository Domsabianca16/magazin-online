package com.ecommerce.backend.repository;

import com.ecommerce.backend.entity.StoreOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StoreOrderRepository extends JpaRepository<StoreOrder, Long> {
    Optional<StoreOrder> findByIdempotencyKey(String idempotencyKey);
}
