package com.bytebazaar.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity @Table(name = "order_tracking")
@Getter @Setter @NoArgsConstructor
public class OrderTracking {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) private Order order;
    @Enumerated(EnumType.STRING) private OrderStatus status;
    private LocalDateTime eventTime = LocalDateTime.now();
    private String location;
    @Column(length = 500) private String description;
}
