package com.bytebazaar.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity @Table(name = "contact_messages")
@Getter @Setter @NoArgsConstructor
public class ContactMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String name; private String email; private String phone; private String subject;
    @Column(columnDefinition = "TEXT") private String message;
    @ManyToOne private User user;
    private String status = "OPEN";
    private LocalDateTime createdAt = LocalDateTime.now();
}
