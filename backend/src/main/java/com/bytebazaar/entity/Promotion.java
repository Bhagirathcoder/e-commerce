package com.bytebazaar.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity @Table(name = "promotions")
@Getter @Setter @NoArgsConstructor
public class Promotion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) private PromotionType type;
    private String name; private String brandName;
    @Column(length = 1000) private String description;
    private String bannerUrl;
    private int discountPercent;
    private LocalDateTime startDate; private LocalDateTime endDate;
    private boolean enabled = true;
    @ManyToMany @JoinTable(name = "sale_products",
            joinColumns = @JoinColumn(name = "promotion_id"), inverseJoinColumns = @JoinColumn(name = "product_id"))
    private Set<Product> products = new HashSet<>();
    public boolean isLive() {
        LocalDateTime n = LocalDateTime.now();
        return enabled && (startDate == null || !n.isBefore(startDate)) && (endDate == null || !n.isAfter(endDate));
    }
}
