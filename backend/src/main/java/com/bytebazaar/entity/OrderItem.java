package com.bytebazaar.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity @Table(name = "order_items")
@Getter @Setter @NoArgsConstructor
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) private Order order;
    @ManyToOne(optional = false) private Product product;
    @ManyToOne(optional = false) private User seller;
    private String productName;
    private String imageUrl;
    private int quantity;
    private BigDecimal mrp;
    private BigDecimal unitPrice;
    private boolean returnable;
    private int warrantyMonths;
    public BigDecimal lineTotal() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
}
