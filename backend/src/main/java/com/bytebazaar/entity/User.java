package com.bytebazaar.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity @Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String fullName;
    @Column(nullable = false, unique = true) private String email;
    @Column(nullable = false, unique = true, length = 15) private String phone;
    @Column(nullable = false) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Role role = Role.USER;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AccountStatus status = AccountStatus.ACTIVE;
    private String address; private String city; private String state; private String pin;
    private String profileImage;
    private String storeName;
    private String resetToken;
    private LocalDateTime resetExpiry;
    private LocalDateTime createdAt = LocalDateTime.now();
}
