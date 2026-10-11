package com.bytebazaar.controller;

import com.bytebazaar.dto.Dtos.*;
import com.bytebazaar.entity.*;
import com.bytebazaar.repository.BannerRepository;
import com.bytebazaar.service.*;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Every route here requires ROLE_ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final StatsService stats;
    private final ProductService products;
    private final OrderService orders;
    private final ReturnService returns;
    private final BankService banks;
    private final SupportService support;
    private final PromotionService promos;
    private final BannerRepository banners;
    private final AnalyticsService analytics;

    @GetMapping("/analytics") public Map<String, Object> analytics(@RequestParam(defaultValue = "30") int days) { return analytics.report(null, days); }

    @GetMapping("/dashboard") public Map<String, Object> dashboard() { return stats.adminDashboard(); }

    // ---- users
    @GetMapping("/users") public List<AdminUserDto> users() { return stats.listByRole(Role.USER); }
    @PutMapping("/users/{id}") public UserDto editUser(@PathVariable Long id, @Valid @RequestBody ProfileReq r) { return stats.adminEdit(id, r); }
    @PutMapping("/users/{id}/block") public Map<String, String> blockUser(@AuthenticationPrincipal User a, @PathVariable Long id) { stats.setStatus(id, Role.USER, AccountStatus.BLOCKED, a); return Map.of("message", "User blocked"); }
    @PutMapping("/users/{id}/unblock") public Map<String, String> unblockUser(@AuthenticationPrincipal User a, @PathVariable Long id) { stats.setStatus(id, Role.USER, AccountStatus.ACTIVE, a); return Map.of("message", "User unblocked"); }
    @DeleteMapping("/users/{id}") public Map<String, String> delUser(@AuthenticationPrincipal User a, @PathVariable Long id) { stats.deleteAccount(id, a); return Map.of("message", "User deleted"); }

    // ---- sellers
    @GetMapping("/sellers") public List<AdminUserDto> sellers() { return stats.listByRole(Role.SELLER); }
    @PutMapping("/sellers/{id}") public UserDto editSeller(@PathVariable Long id, @Valid @RequestBody ProfileReq r) { return stats.adminEdit(id, r); }
    @PutMapping("/sellers/{id}/block") public Map<String, String> blockSeller(@AuthenticationPrincipal User a, @PathVariable Long id) { stats.setStatus(id, Role.SELLER, AccountStatus.BLOCKED, a); return Map.of("message", "Seller blocked"); }
    @PutMapping("/sellers/{id}/unblock") public Map<String, String> unblockSeller(@AuthenticationPrincipal User a, @PathVariable Long id) { stats.setStatus(id, Role.SELLER, AccountStatus.ACTIVE, a); return Map.of("message", "Seller unblocked"); }
    @DeleteMapping("/sellers/{id}") public Map<String, String> delSeller(@AuthenticationPrincipal User a, @PathVariable Long id) { stats.deleteAccount(id, a); return Map.of("message", "Seller deleted"); }

    // ---- products & categories
    @GetMapping("/products") public List<ProductDto> products() { return products.forAdmin(); }
    @PostMapping("/products") public ResponseEntity<ProductDto> addProduct(@AuthenticationPrincipal User a, @Valid @RequestBody ProductReq r) { return ResponseEntity.status(HttpStatus.CREATED).body(products.create(a, r)); }
    @PutMapping("/products/{id}") public ProductDto editProduct(@AuthenticationPrincipal User a, @PathVariable Long id, @Valid @RequestBody ProductReq r) { return products.update(a, id, r); }
    @DeleteMapping("/products/{id}") public Map<String, String> delProduct(@AuthenticationPrincipal User a, @PathVariable Long id) { products.delete(a, id); return Map.of("message", "Product deleted"); }
    @PutMapping("/products/{id}/verify") public ProductDto verify(@PathVariable Long id) { return products.verify(id, true); }
    @PutMapping("/products/{id}/reject") public ProductDto reject(@PathVariable Long id) { return products.verify(id, false); }
    @PutMapping("/products/{id}/active") public ProductDto active(@AuthenticationPrincipal User a, @PathVariable Long id, @RequestParam boolean value) { return products.setActive(a, id, value); }
    @PostMapping("/categories") public CategoryDto addCategory(@Valid @RequestBody CategoryReq r) { return products.addCategory(r); }
    @DeleteMapping("/categories/{id}") public Map<String, String> delCategory(@PathVariable Long id) { products.deleteCategory(id); return Map.of("message", "Category deleted"); }

    // ---- orders / returns / refunds
    @GetMapping("/orders") public List<OrderDto> orders() { return orders.all(); }
    @PutMapping("/orders/{id}/status") public OrderDto orderStatus(@PathVariable Long id, @Valid @RequestBody StatusReq r) { return orders.adminUpdate(id, r); }
    @GetMapping("/returns") public List<ReturnDto> returns() { return returns.all(); }
    @PutMapping("/returns/{id}/status") public ReturnDto returnStatus(@PathVariable Long id, @Valid @RequestBody ReturnActionReq r) { return returns.advance(id, r.status(), r.note()); }
    @GetMapping("/refunds") public List<RefundDto> refunds() { return returns.allRefunds(); }
    @PutMapping("/refunds/{id}/complete") public RefundDto completeRefund(@PathVariable Long id) { return returns.completeRefund(id); }

    // ---- commissions
    @GetMapping("/commissions") public List<CommissionDto> commissions() { return stats.allCommissions(); }
    @PutMapping("/commissions/{id}/paid") public CommissionDto paid(@PathVariable Long id) { return stats.markPaid(id); }
    @PutMapping("/settings/commission") public Map<String, Object> setRate(@Valid @RequestBody CommissionRateReq r) { stats.setCommissionPercent(r.percent()); return Map.of("commissionPercent", r.percent()); }

    // ---- admin's own (demo) bank account
    @GetMapping("/bank-account") public List<BankDto> bank(@AuthenticationPrincipal User a) { return banks.list(a); }
    @PutMapping("/bank-account/{id}") public BankDto editBank(@AuthenticationPrincipal User a, @PathVariable Long id, @Valid @RequestBody BankReq r) { return banks.update(a, id, r); }
    @PutMapping("/bank-account/{id}/active") public BankDto bankActive(@AuthenticationPrincipal User a, @PathVariable Long id, @RequestParam boolean value) { return banks.setActive(a, id, value); }

    // ---- marketing: banners, festival sales, brand deals
    @GetMapping("/banners") public List<Banner> allBanners() { return banners.findAllByOrderBySortOrderAscIdAsc(); }
    @PostMapping("/banners") public Banner addBanner(@Valid @RequestBody BannerReq r) { return banners.save(applyBanner(new Banner(), r)); }
    @PutMapping("/banners/{id}") public Banner editBanner(@PathVariable Long id, @Valid @RequestBody BannerReq r) {
        return banners.save(applyBanner(banners.findById(id).orElseThrow(() -> new com.bytebazaar.exception.NotFoundException("Banner not found")), r));
    }
    @DeleteMapping("/banners/{id}") public Map<String, String> delBanner(@PathVariable Long id) { banners.deleteById(id); return Map.of("message", "Banner deleted"); }
    private Banner applyBanner(Banner b, BannerReq r) {
        b.setSaleTitle(r.saleTitle()); b.setProductName(r.productName()); b.setDiscountText(r.discountText()); b.setImageUrl(r.imageUrl());
        b.setCtaText(r.ctaText() == null || r.ctaText().isBlank() ? "Shop Now" : r.ctaText()); b.setLink(r.link());
        b.setBgColor(r.bgColor() == null || r.bgColor().isBlank() ? "#0f2742" : r.bgColor()); b.setSortOrder(r.sortOrder()); b.setActive(r.active());
        return b;
    }

    @GetMapping("/promotions") public List<PromotionDto> promotions() { return promos.all(); }
    @PostMapping("/promotions") public PromotionDto addPromo(@Valid @RequestBody PromotionReq r) { return promos.save(null, r); }
    @PutMapping("/promotions/{id}") public PromotionDto editPromo(@PathVariable Long id, @Valid @RequestBody PromotionReq r) { return promos.save(id, r); }
    @DeleteMapping("/promotions/{id}") public Map<String, String> delPromo(@PathVariable Long id) { promos.delete(id); return Map.of("message", "Deleted"); }

    // ---- support
    @GetMapping("/support") public List<ConversationDto> conversations() { return support.conversations(); }
    @GetMapping("/support/{userId}") public List<ChatMsgDto> thread(@PathVariable Long userId) { return support.adminThread(userId); }
    @PostMapping("/support/{userId}") public ChatMsgDto reply(@AuthenticationPrincipal User a, @PathVariable Long userId, @Valid @RequestBody ChatReq r) { return support.adminReply(a, userId, r.message()); }
    @GetMapping("/contact-messages") public List<Map<String, Object>> contacts() { return support.contactMessages(); }
    @PutMapping("/contact-messages/{id}/close") public Map<String, String> closeContact(@PathVariable Long id) { support.closeContact(id); return Map.of("message", "Closed"); }
}
