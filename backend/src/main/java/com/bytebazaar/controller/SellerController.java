package com.bytebazaar.controller;

import com.bytebazaar.dto.Dtos.*;
import com.bytebazaar.entity.User;
import com.bytebazaar.service.*;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** /api/seller/** is restricted to ROLE_SELLER by SecurityConfig; blocked sellers are rejected by JwtAuthFilter. */
@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class SellerController {
    private final ProductService products;
    private final OrderService orders;
    private final StatsService stats;
    private final BankService banks;
    private final ReturnService returns;
    private final AnalyticsService analytics;

    @GetMapping("/analytics") public Map<String, Object> analytics(@AuthenticationPrincipal User s, @RequestParam(defaultValue = "30") int days) { return analytics.report(s.getId(), days); }

    @GetMapping("/dashboard") public Map<String, Object> dashboard(@AuthenticationPrincipal User s) { return stats.sellerDashboard(s); }

    @PostMapping("/products")
    public ResponseEntity<ProductDto> add(@AuthenticationPrincipal User s, @Valid @RequestBody ProductReq r) { return ResponseEntity.status(HttpStatus.CREATED).body(products.create(s, r)); }
    @GetMapping("/products") public List<ProductDto> mine(@AuthenticationPrincipal User s) { return products.forSeller(s); }
    @GetMapping("/products/{id}") public ProductDto one(@AuthenticationPrincipal User s, @PathVariable Long id) { return products.get(id, s); }
    @PutMapping("/products/{id}") public ProductDto edit(@AuthenticationPrincipal User s, @PathVariable Long id, @Valid @RequestBody ProductReq r) { return products.update(s, id, r); }
    @PutMapping("/products/{id}/active") public ProductDto active(@AuthenticationPrincipal User s, @PathVariable Long id, @RequestParam boolean value) { return products.setActive(s, id, value); }
    @DeleteMapping("/products/{id}") public Map<String, String> delete(@AuthenticationPrincipal User s, @PathVariable Long id) { products.delete(s, id); return Map.of("message", "Product deleted"); }

    @GetMapping("/orders") public List<OrderDto> orders(@AuthenticationPrincipal User s) { return orders.forSeller(s); }
    @PutMapping("/orders/{id}/status") public OrderDto status(@AuthenticationPrincipal User s, @PathVariable Long id, @Valid @RequestBody StatusReq r) { return orders.sellerUpdate(s, id, r); }
    @GetMapping("/returns") public List<ReturnDto> returns(@AuthenticationPrincipal User s) { return returns.forSeller(s); }

    @GetMapping("/commission")
    public Map<String, Object> commission(@AuthenticationPrincipal User s) {
        Map<String, Object> m = new LinkedHashMap<>(stats.sellerDashboard(s));
        m.put("records", stats.commissionsFor(s));
        return m;
    }
    @GetMapping("/bank-account") public List<BankDto> bank(@AuthenticationPrincipal User s) { return banks.list(s); }
}
