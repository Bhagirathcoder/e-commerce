package com.bytebazaar.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity @Table(name = "return_tracking")
@Getter @Setter @NoArgsConstructor
public class ReturnTracking {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) private ReturnRequest returnRequest;
    @Enumerated(EnumType.STRING) private ReturnStatus status;
    private LocalDateTime eventTime = LocalDateTime.now();
    @Column(length = 500) private String description;
}
