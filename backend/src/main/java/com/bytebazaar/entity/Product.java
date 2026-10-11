package com.bytebazaar.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_product_name", columnList = "name"),
        @Index(name = "idx_product_visible", columnList = "verification,active,deleted")})
@Getter @Setter @NoArgsConstructor
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String name;
    private String brand;
    @ManyToOne(optional = false) private Category category;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(columnDefinition = "TEXT") private String specifications;
    @Column(unique = true) private String sku;
    @Column(length = 500) private String tags;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal price;
    private int discountPercent;
    private int stock;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "image_url", length = 500)
    @OrderColumn(name = "position")
    private List<String> images = new ArrayList<>();
    private int warrantyMonths;
    private boolean returnable = true;
    @ManyToOne(optional = false) private User seller;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private VerificationStatus verification = VerificationStatus.PENDING_VERIFICATION;
    private boolean active = true;
    private boolean deleted = false;
    private double avgRating;
    private int reviewCount;
    private int soldCount;
    private LocalDateTime createdAt = LocalDateTime.now();

    public BigDecimal finalPrice(int discount) {
        return price.multiply(BigDecimal.valueOf(100L - discount)).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    }
}
