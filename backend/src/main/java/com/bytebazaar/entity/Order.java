package com.bytebazaar.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity @Table(name = "orders", indexes = @Index(name = "idx_order_user", columnList = "user_id"))
@Getter @Setter @NoArgsConstructor
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) private User user;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OrderStatus status = OrderStatus.PLACED;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentMethod paymentMethod;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentStatus paymentStatus = PaymentStatus.PENDING;
    private BigDecimal subtotal; private BigDecimal discount; private BigDecimal shipping; private BigDecimal tax; private BigDecimal total;
    private String shipName; private String shipPhone; private String shipAddress; private String shipCity;
    private String shipDistrict; private String shipState; private String shipPin;
    private String invoiceNumber;
    private LocalDate estimatedDelivery;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt = LocalDateTime.now();
}
