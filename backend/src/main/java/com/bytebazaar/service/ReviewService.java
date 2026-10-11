package com.bytebazaar.service;

import com.bytebazaar.dto.Dtos.*;
import com.bytebazaar.entity.*;
import com.bytebazaar.exception.*;
import com.bytebazaar.repository.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewService {
    private final ReviewRepository reviews;
    private final ProductRepository products;
    private final OrderItemRepository items;
    private final FileStorageService files;

    private ReviewDto dto(Review r) {
        return new ReviewDto(r.getId(), r.getProduct().getId(), r.getProduct().getName(), r.getUser().getFullName(), r.getRating(),
                r.getTitle(), r.getBody(), r.isVerifiedPurchase(), List.copyOf(r.getImages()), r.getCreatedAt());
    }

    public ReviewDto add(User u, Long productId, int rating, String title, String body, List<MultipartFile> photos) {
        if (rating < 1 || rating > 5) throw new InvalidRequestException("Rating must be between 1 and 5 stars");
        Product p = products.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        // Only buyers whose order was actually DELIVERED may review (no fake reviews).
        if (!items.existsPurchase(u.getId(), productId, OrderStatus.DELIVERED))
            throw new ForbiddenException("Only customers who received this product can review it");
        if (reviews.existsByUserAndProduct(u, p)) throw new ConflictException("You have already reviewed this product");
        Review r = new Review();
        r.setProduct(p); r.setUser(u); r.setRating(rating); r.setTitle(title); r.setBody(body); r.setVerifiedPurchase(true);
        r.setImages(files.storeAll(photos, "reviews", 5));
        reviews.save(r);
        reviews.flush();
        p.setReviewCount((int) reviews.countByProduct(p));
        p.setAvgRating(reviews.avgFor(p));
        return dto(r);
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> forProduct(Long productId) {
        Product p = products.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        return reviews.findByProductOrderByCreatedAtDesc(p).stream().map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> recent() { return reviews.findTop8ByRatingGreaterThanEqualOrderByCreatedAtDesc(4).stream().map(this::dto).toList(); }
}
