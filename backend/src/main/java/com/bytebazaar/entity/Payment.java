package com.bytebazaar.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity @Table(name = "payments")
@Getter @Setter @NoArgsConstructor
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) private Order order;
    @Enumerated(EnumType.STRING) private PaymentMethod method;
    @Enumerated(EnumType.STRING) private PaymentStatus status = PaymentStatus.PENDING;
    private BigDecimal amount;
    private String gatewayRef;
    /** What the payment gateway reports. Only the server-side gateway adapter reads this. */
    private String gatewayStatus = "CREATED";
    private String upiId;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime verifiedAt;
}
