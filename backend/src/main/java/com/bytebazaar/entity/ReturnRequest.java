package com.bytebazaar.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity @Table(name = "returns")
@Getter @Setter @NoArgsConstructor
public class ReturnRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) private OrderItem orderItem;
    @ManyToOne(optional = false) private Order order;
    @ManyToOne(optional = false) private User user;
    private String reason;
    @Column(length = 1000) private String userComment;
    @Enumerated(EnumType.STRING) private ReturnStatus status = ReturnStatus.REQUESTED;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "return_images", joinColumns = @JoinColumn(name = "return_id"))
    @Column(name = "image_url") private List<String> images = new ArrayList<>();
    @Column(length = 1000) private String analysisNote;
    private LocalDateTime requestedAt = LocalDateTime.now();
}
