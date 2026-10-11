package com.bytebazaar.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity @Table(name = "notifications", indexes = @Index(name = "idx_notif_user", columnList = "user_id"))
@Getter @Setter @NoArgsConstructor
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) private User user;
    @Enumerated(EnumType.STRING) private NotificationType type;
    @Column(length = 500) private String message;
    private boolean seen = false;
    private LocalDateTime createdAt = LocalDateTime.now();
}
