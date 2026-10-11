package com.bytebazaar.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity @Table(name = "reviews", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"}))
@Getter @Setter @NoArgsConstructor
public class Review {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) private Product product;
    @ManyToOne(optional = false) private User user;
    private int rating;
    private String title;
    @Column(columnDefinition = "TEXT") private String body;
    private boolean verifiedPurchase = true;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "review_images", joinColumns = @JoinColumn(name = "review_id"))
    @Column(name = "image_url") private List<String> images = new ArrayList<>();
    private LocalDateTime createdAt = LocalDateTime.now();
}
