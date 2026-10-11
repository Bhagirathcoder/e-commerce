package com.bytebazaar.service;

import com.bytebazaar.dto.Dtos.*;
import com.bytebazaar.entity.*;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {
    public static int effectiveDiscount(Product p, Map<Long, Integer> live) {
        return Math.max(p.getDiscountPercent(), live.getOrDefault(p.getId(), 0));
    }

    public ProductDto toDto(Product p, Map<Long, Integer> live) {
        int d = effectiveDiscount(p, live);
        Category c = p.getCategory();
        return new ProductDto(p.getId(), p.getName(), p.getBrand(), c.getName(), c.getId(),
                c.getParent() == null ? null : c.getParent().getName(),
                p.getDescription(), p.getSpecifications(), p.getSku(), p.getTags(),
                p.getPrice(), d, p.finalPrice(d), p.getStock(), p.getStock() > 0,
                List.copyOf(p.getImages()), p.getWarrantyMonths(), p.isReturnable(),
                p.getSeller().getId(), p.getSeller().getStoreName() != null ? p.getSeller().getStoreName() : p.getSeller().getFullName(),
                p.getVerification(), p.isActive(), Math.round(p.getAvgRating() * 10.0) / 10.0, p.getReviewCount(),
                p.getSoldCount(), p.getCreatedAt(), live.containsKey(p.getId()) && live.get(p.getId()) > p.getDiscountPercent());
    }
}
