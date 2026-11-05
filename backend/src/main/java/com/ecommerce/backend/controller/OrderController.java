package com.ecommerce.backend.controller;

import com.ecommerce.backend.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;
    public OrderController(OrderService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderService.OrderResult createOrder(@RequestHeader("Idempotency-Key") String key,
                                                @RequestBody OrderService.CreateOrderRequest request) {
        return service.create(key, request);
    }
}
