package com.bytebazaar.controller;

import com.bytebazaar.dto.Dtos.*;
import com.bytebazaar.entity.Banner;
import com.bytebazaar.entity.User;
import com.bytebazaar.repository.BannerRepository;
import com.bytebazaar.service.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Public catalog endpoints (no login needed). */
@RestController
@RequiredArgsConstructor
public class CatalogController {
    private final ProductService products;
    private final BannerRepository banners;
    private final PromotionService promos;
    private final ReviewService reviews;

    @GetMapping("/api/products")
    public PageDto<ProductDto> list(@RequestParam(required = false) String q, @RequestParam(required = false) String category,
                                    @RequestParam(required = false) String brand, @RequestParam(required = false) BigDecimal minPrice,
                                    @RequestParam(required = false) BigDecimal maxPrice, @RequestParam(required = false) Double minRating,
                                    @RequestParam(required = false) Boolean inStock, @RequestParam(required = false) Integer minDiscount,
                                    @RequestParam(required = false) String sort, @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return products.search(q, category, brand, minPrice, maxPrice, minRating, inStock, minDiscount, sort, page, size);
    }

    @GetMapping("/api/products/search")
    public PageDto<ProductDto> search(@RequestParam String q, @RequestParam(defaultValue = "relevance") String sort,
                                      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return products.search(q, null, null, null, null, null, null, null, sort, page, size);
    }

    @GetMapping("/api/products/category/{category}")
    public PageDto<ProductDto> byCategory(@PathVariable String category, @RequestParam(defaultValue = "relevance") String sort,
                                          @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return products.search(null, category, null, null, null, null, null, null, sort, page, size);
    }

    @GetMapping("/api/products/{id}")
    public ProductDto one(@PathVariable Long id, @AuthenticationPrincipal User u) { return products.get(id, u); }

    @GetMapping("/api/products/related/{id}")
    public List<ProductDto> related(@PathVariable Long id) { return products.related(id); }

    @GetMapping("/api/products/{id}/reviews")
    public List<ReviewDto> productReviews(@PathVariable Long id) { return reviews.forProduct(id); }

    @GetMapping("/api/products/by-ids")
    public List<ProductDto> byIds(@RequestParam List<Long> ids) { return products.byIds(ids.size() > 30 ? ids.subList(0, 30) : ids); }

    @GetMapping("/api/reviews/recent")
    public List<ReviewDto> recentReviews() { return reviews.recent(); }

    @GetMapping("/api/categories")
    public List<CategoryDto> categories() { return products.categoryTree(); }

    @GetMapping("/api/brands")
    public List<String> brands() { return products.brands(); }

    @GetMapping("/api/banners")
    public List<Banner> banners() { return banners.findByActiveTrueOrderBySortOrderAscIdAsc(); }

    @GetMapping("/api/promotions")
    public List<PromotionDto> promotions() { return promos.live(); }

    @GetMapping("/api/home")
    public Map<String, Object> home() {
        Map<String, Object> m = new LinkedHashMap<>(products.home());
        m.put("banners", banners.findByActiveTrueOrderBySortOrderAscIdAsc());
        m.put("promotions", promos.live());
        m.put("categories", products.categoryTree());
        return m;
    }
}
