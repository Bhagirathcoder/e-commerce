package com.bytebazaar.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity @Table(name = "bank_accounts")
@Getter @Setter @NoArgsConstructor
public class BankAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) private User user;
    private String holderName; private String bankName;
    private String accountNumber;
    private String ifsc; private String upiId;
    private boolean primaryAccount;
    private boolean demo;
    private boolean active = true;
}
