package com.ecommerce.backend.controller;

import com.ecommerce.backend.service.AdminService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;
    public AdminController(AdminService adminService) { this.adminService = adminService; }
    @GetMapping("/session") public Map<String, String> session(Authentication auth) { return Map.of("username", auth.getName(), "role", "ADMIN"); }
    @GetMapping("/products") public List<?> products() { return adminService.getProducts(); }
    @GetMapping("/orders") public List<AdminService.OrderView> orders() { return adminService.getOrders(); }
    @GetMapping("/customers") public List<AdminService.CustomerView> customers() { return adminService.getCustomers(); }
    @PatchMapping("/orders/{id}/status") public AdminService.OrderView updateStatus(@PathVariable Long id, @RequestBody Map<String,String> body) { return adminService.updateOrderStatus(id, body.get("status")); }
    @PatchMapping("/orders/{id}/payment") public AdminService.OrderView markPaid(@PathVariable Long id) { return adminService.markPaid(id); }
    @PostMapping("/orders/{id}/refund") @ResponseStatus(HttpStatus.OK) public AdminService.OrderView recordRefund(@PathVariable Long id, @RequestBody(required=false) Map<String,String> body) { return adminService.recordManualRefund(id, body == null ? null : body.get("note")); }
}
