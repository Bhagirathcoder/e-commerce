package com.bytebazaar.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity @Table(name = "banners")
@Getter @Setter @NoArgsConstructor
public class Banner {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String saleTitle; private String productName; private String discountText;
    private String imageUrl; private String ctaText = "Shop Now"; private String link;
    private String bgColor = "#0f2742";
    private int sortOrder;
    private boolean active = true;
}
