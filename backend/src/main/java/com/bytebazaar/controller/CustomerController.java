package com.bytebazaar.controller;

import com.bytebazaar.dto.Dtos.*;
import com.bytebazaar.entity.*;
import com.bytebazaar.repository.NotificationRepository;
import com.bytebazaar.service.*;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Everything a logged-in customer does: cart, wishlist, orders, payments, returns, reviews, notifications, bank, support. */
@RestController
@RequiredArgsConstructor
public class CustomerController {
    private final ShopService shop;
    private final OrderService orders;
    private final PaymentService payments;
    private final ReturnService returns;
    private final ReviewService reviews;
    private final BankService banks;
    private final SupportService support;
    private final AiSupportService ai;
    private final PdfService pdf;
    private final NotificationRepository notifs;

    // ------------------------------------------------------------ cart
    @GetMapping("/api/cart") public CartDto cart(@AuthenticationPrincipal User u) { return shop.cart(u); }
    @PostMapping("/api/cart/items") public CartDto addCart(@AuthenticationPrincipal User u, @Valid @RequestBody CartItemReq r) { return shop.addToCart(u, r); }
    @PutMapping("/api/cart/items/{id}") public CartDto qty(@AuthenticationPrincipal User u, @PathVariable Long id, @Valid @RequestBody QtyReq r) { return shop.updateQty(u, id, r.quantity()); }
    @DeleteMapping("/api/cart/items/{id}") public CartDto removeCart(@AuthenticationPrincipal User u, @PathVariable Long id) { return shop.remove(u, id); }
    @PostMapping("/api/cart/items/{id}/move-to-wishlist") public CartDto toWish(@AuthenticationPrincipal User u, @PathVariable Long id) { return shop.moveToWishlist(u, id); }

    // ------------------------------------------------------------ wishlist
    @GetMapping("/api/wishlist") public List<ProductDto> wishlist(@AuthenticationPrincipal User u) { return shop.wishlist(u); }
    @PostMapping("/api/wishlist/{productId}") public Map<String, String> addWish(@AuthenticationPrincipal User u, @PathVariable Long productId) { shop.addWishlist(u, productId); return Map.of("message", "Added to wishlist"); }
    @DeleteMapping("/api/wishlist/{productId}") public Map<String, String> delWish(@AuthenticationPrincipal User u, @PathVariable Long productId) { shop.removeWishlist(u, productId); return Map.of("message", "Removed from wishlist"); }

    // ------------------------------------------------------------ orders
    @PostMapping("/api/orders") public ResponseEntity<OrderDto> place(@AuthenticationPrincipal User u, @Valid @RequestBody OrderReq r) { return ResponseEntity.status(HttpStatus.CREATED).body(orders.place(u, r)); }
    @GetMapping("/api/orders") public List<OrderDto> myOrders(@AuthenticationPrincipal User u) { return orders.mine(u); }
    @GetMapping("/api/orders/{id}") public OrderDto order(@AuthenticationPrincipal User u, @PathVariable Long id) { return orders.get(u, id); }
    @GetMapping("/api/orders/{id}/tracking") public List<TrackingDto> tracking(@AuthenticationPrincipal User u, @PathVariable Long id) { return orders.tracking(u, id); }
    @PutMapping("/api/orders/{id}/cancel") public OrderDto cancel(@AuthenticationPrincipal User u, @PathVariable Long id) { return orders.cancel(u, id); }

    @GetMapping("/api/orders/{id}/invoice")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> invoice(@AuthenticationPrincipal User u, @PathVariable Long id) {
        Order o = orders.viewable(u, id);
        Long sellerFilter = u.getRole() == Role.SELLER ? u.getId() : null;
        return pdfResponse(pdf.invoice(o, sellerFilter), "ByteBazaar-Invoice-" + id + ".pdf");
    }

    @GetMapping("/api/orders/{id}/warranty")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> warranty(@AuthenticationPrincipal User u, @PathVariable Long id, @RequestParam(required = false) Long itemId) {
        Order o = orders.viewable(u, id);
        return pdfResponse(pdf.warranty(o, itemId), "ByteBazaar-Warranty-" + id + ".pdf");
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] data, String name) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"").body(data);
    }

    // ------------------------------------------------------------ payments (UPI)
    @PostMapping("/api/payments/create") public PaymentDto payCreate(@AuthenticationPrincipal User u, @Valid @RequestBody PaymentCreateReq r) { return payments.create(u, r.orderId()); }
    @PostMapping("/api/payments/simulate") public PaymentDto paySim(@AuthenticationPrincipal User u, @Valid @RequestBody PaymentIdReq r) { return payments.simulatePay(u, r.paymentId()); }
    @PostMapping("/api/payments/verify") public PaymentDto payVerify(@AuthenticationPrincipal User u, @Valid @RequestBody PaymentIdReq r) { return payments.verify(u, r.paymentId()); }

    // ------------------------------------------------------------ returns / refunds
    @PostMapping(value = "/api/returns", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReturnDto> requestReturn(@AuthenticationPrincipal User u, @RequestParam Long orderItemId, @RequestParam String reason,
                                                   @RequestParam(required = false) String comment,
                                                   @RequestParam(value = "photos", required = false) List<MultipartFile> photos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(returns.request(u, orderItemId, reason, comment, photos));
    }
    @GetMapping("/api/returns") public List<ReturnDto> myReturns(@AuthenticationPrincipal User u) { return returns.mine(u); }
    @GetMapping("/api/returns/{id}") public ReturnDto oneReturn(@AuthenticationPrincipal User u, @PathVariable Long id) { return returns.get(u, id, true); }
    @GetMapping("/api/returns/{id}/tracking") public List<ReturnTrackingDto> returnTracking(@AuthenticationPrincipal User u, @PathVariable Long id) { return returns.get(u, id, true).tracking(); }
    @GetMapping("/api/refunds") public List<RefundDto> myRefunds(@AuthenticationPrincipal User u) { return returns.refundsFor(u); }

    // ------------------------------------------------------------ reviews
    @PostMapping(value = "/api/products/{id}/reviews", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReviewDto> review(@AuthenticationPrincipal User u, @PathVariable Long id, @RequestParam int rating,
                                            @RequestParam(required = false) String title, @RequestParam(required = false) String body,
                                            @RequestParam(value = "photos", required = false) List<MultipartFile> photos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviews.add(u, id, rating, title, body, photos));
    }

    // ------------------------------------------------------------ notifications
    private NotificationDto nd(Notification n) { return new NotificationDto(n.getId(), n.getType(), n.getMessage(), n.isSeen(), n.getCreatedAt()); }

    @GetMapping("/api/notifications")
    public Map<String, Object> notifications(@AuthenticationPrincipal User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unread", notifs.countByUserAndSeenFalse(u));
        m.put("items", notifs.findByUserOrderByCreatedAtDesc(u, PageRequest.of(0, 100)).stream().map(this::nd).toList());
        return m;
    }
    @PutMapping("/api/notifications/{id}/read")
    @Transactional
    public Map<String, String> read(@AuthenticationPrincipal User u, @PathVariable Long id) {
        notifs.findByIdAndUser(id, u).ifPresent(n -> n.setSeen(true)); return Map.of("message", "ok");
    }
    @PutMapping("/api/notifications/read-all")
    @Transactional
    public Map<String, String> readAll(@AuthenticationPrincipal User u) { notifs.markAllSeen(u); return Map.of("message", "ok"); }
    @DeleteMapping("/api/notifications/{id}")
    @Transactional
    public Map<String, String> delNotif(@AuthenticationPrincipal User u, @PathVariable Long id) { notifs.findByIdAndUser(id, u).ifPresent(notifs::delete); return Map.of("message", "ok"); }

    // ------------------------------------------------------------ bank accounts (users AND sellers; masked output)
    @GetMapping("/api/bank-accounts") public List<BankDto> banks(@AuthenticationPrincipal User u) { return banks.list(u); }
    @PostMapping("/api/bank-accounts") public ResponseEntity<BankDto> addBank(@AuthenticationPrincipal User u, @Valid @RequestBody BankReq r) { return ResponseEntity.status(HttpStatus.CREATED).body(banks.add(u, r)); }
    @PutMapping("/api/bank-accounts/{id}") public BankDto editBank(@AuthenticationPrincipal User u, @PathVariable Long id, @Valid @RequestBody BankReq r) { return banks.update(u, id, r); }
    @PutMapping("/api/bank-accounts/{id}/primary") public BankDto primary(@AuthenticationPrincipal User u, @PathVariable Long id) { return banks.setPrimary(u, id); }
    @PutMapping("/api/bank-accounts/{id}/active") public BankDto active(@AuthenticationPrincipal User u, @PathVariable Long id, @RequestParam boolean value) { return banks.setActive(u, id, value); }
    @DeleteMapping("/api/bank-accounts/{id}") public Map<String, String> delBank(@AuthenticationPrincipal User u, @PathVariable Long id) { banks.delete(u, id); return Map.of("message", "Bank account removed"); }

    // ------------------------------------------------------------ support
    @PostMapping("/api/support/ai") public AiReply aiChat(@AuthenticationPrincipal User u, @Valid @RequestBody ChatReq r) { return ai.answer(u, r.message(), r.orderId()); }
    @GetMapping("/api/support/messages") public List<ChatMsgDto> thread(@AuthenticationPrincipal User u) { return support.userThread(u); }
    @PostMapping("/api/support/messages") public ChatMsgDto send(@AuthenticationPrincipal User u, @Valid @RequestBody ChatReq r) { return support.userSend(u, r.message()); }
    @PostMapping("/api/contact")
    public Map<String, String> contact(@AuthenticationPrincipal User u, @Valid @RequestBody ContactReq r) {
        support.contact(u, r); return Map.of("message", "Thanks! Your message has been sent. We'll get back to you soon.");
    }
}
