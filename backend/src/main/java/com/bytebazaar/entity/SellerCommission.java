package com.bytebazaar.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity @Table(name = "seller_commissions")
@Getter @Setter @NoArgsConstructor
public class SellerCommission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) private User seller;
    @ManyToOne(optional = false) private Order order;
    @ManyToOne(optional = false) private OrderItem orderItem;
    @ManyToOne(optional = false) private Product product;
    private BigDecimal saleAmount; private BigDecimal commissionPct;
    private BigDecimal commissionAmount; private BigDecimal sellerAmount;
    @Enumerated(EnumType.STRING) private CommissionStatus paymentStatus = CommissionStatus.PENDING;
    private LocalDateTime createdAt = LocalDateTime.now();
}
