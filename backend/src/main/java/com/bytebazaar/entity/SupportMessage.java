package com.bytebazaar.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity @Table(name = "support_messages", indexes = @Index(name = "idx_support_user", columnList = "user_id"))
@Getter @Setter @NoArgsConstructor
public class SupportMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    /** The customer this conversation belongs to (conversation id == customer id). */
    @ManyToOne(optional = false) private User user;
    @ManyToOne private User admin;
    @Enumerated(EnumType.STRING) private SupportSender sender;
    @Column(length = 2000) private String message;
    private boolean seen = false;
    private LocalDateTime createdAt = LocalDateTime.now();
}
