package com.bytebazaar.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity @Table(name = "refunds")
@Getter @Setter @NoArgsConstructor
public class Refund {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne private ReturnRequest returnRequest;
    @ManyToOne(optional = false) private Order order;
    @ManyToOne(optional = false) private User user;
    private BigDecimal amount;
    @Enumerated(EnumType.STRING) private RefundStatus status = RefundStatus.INITIATED;
    private String destination;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime completedAt;
}
