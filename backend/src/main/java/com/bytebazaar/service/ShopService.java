package com.bytebazaar.service;

import com.bytebazaar.dto.Dtos.*;
import com.bytebazaar.entity.*;
import com.bytebazaar.exception.*;
import com.bytebazaar.repository.*;
import com.bytebazaar.util.AppConstants;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cart + wishlist (both stored in MySQL per logged-in user). */
@Service
@RequiredArgsConstructor
@Transactional
public class ShopService {
    private final CartItemRepository carts;
    private final WishlistRepository wishlist;
    private final ProductRepository products;
    private final PromotionService promos;
    private final ProductMapper mapper;

    private Product buyable(Long id) {
        Product p = products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        if (p.isDeleted() || !p.isActive() || p.getVerification() != VerificationStatus.VERIFIED)
            throw new InvalidRequestException("This product is not available");
        return p;
    }

    // ------------------------------------------------------------ cart
    @Transactional(readOnly = true)
    public CartDto cart(User u) {
        Map<Long, Integer> live = promos.liveDiscountMap();
        List<CartItemDto> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO, net = BigDecimal.ZERO;
        int count = 0;
        for (CartItem ci : carts.findByUserOrderByIdAsc(u)) {
            Product p = ci.getProduct();
            ProductDto dto = mapper.toDto(p, live);
            BigDecimal line = dto.finalPrice().multiply(BigDecimal.valueOf(ci.getQuantity()));
            items.add(new CartItemDto(ci.getId(), dto, ci.getQuantity(), line));
            subtotal = subtotal.add(p.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity())));
            net = net.add(line);
            count += ci.getQuantity();
        }
        BigDecimal discount = subtotal.subtract(net);
        BigDecimal shipping = items.isEmpty() || net.compareTo(AppConstants.FREE_SHIPPING_ABOVE) >= 0 ? BigDecimal.ZERO : AppConstants.SHIPPING_FEE;
        BigDecimal tax = net.multiply(AppConstants.TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        return new CartDto(items, count, subtotal, discount, shipping, tax, net.add(shipping).add(tax));
    }

    public CartDto addToCart(User u, CartItemReq r) {
        Product p = buyable(r.productId());
        CartItem ci = carts.findByUserAndProduct(u, p).orElseGet(() -> {
            CartItem n = new CartItem(); n.setUser(u); n.setProduct(p); n.setQuantity(0); return n;
        });
        int q = ci.getQuantity() + r.quantity();
        if (q > 10) q = 10;
        if (q > p.getStock()) throw new InsufficientStockException(p.getName(), p.getStock());
        ci.setQuantity(q);
        carts.save(ci);
        return cart(u);
    }

    public CartDto updateQty(User u, Long itemId, int qty) {
        CartItem ci = carts.findByIdAndUser(itemId, u).orElseThrow(() -> new NotFoundException("Cart item not found"));
        if (qty > ci.getProduct().getStock()) throw new InsufficientStockException(ci.getProduct().getName(), ci.getProduct().getStock());
        ci.setQuantity(qty);
        return cart(u);
    }

    public CartDto remove(User u, Long itemId) {
        CartItem ci = carts.findByIdAndUser(itemId, u).orElseThrow(() -> new NotFoundException("Cart item not found"));
        carts.delete(ci);
        return cart(u);
    }

    public CartDto moveToWishlist(User u, Long itemId) {
        CartItem ci = carts.findByIdAndUser(itemId, u).orElseThrow(() -> new NotFoundException("Cart item not found"));
        addWishlist(u, ci.getProduct().getId());
        carts.delete(ci);
        return cart(u);
    }

    // ------------------------------------------------------------ wishlist
    @Transactional(readOnly = true)
    public List<ProductDto> wishlist(User u) {
        Map<Long, Integer> live = promos.liveDiscountMap();
        return wishlist.findByUserOrderByAddedAtDesc(u).stream().map(w -> mapper.toDto(w.getProduct(), live)).toList();
    }

    public void addWishlist(User u, Long productId) {
        Product p = buyable(productId);
        if (wishlist.findByUserAndProduct(u, p).isEmpty()) {
            WishlistItem w = new WishlistItem(); w.setUser(u); w.setProduct(p); wishlist.save(w);
        }
    }

    public void removeWishlist(User u, Long productId) {
        products.findById(productId).flatMap(p -> wishlist.findByUserAndProduct(u, p)).ifPresent(wishlist::delete);
    }
}
