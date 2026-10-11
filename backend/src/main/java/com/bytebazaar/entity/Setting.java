package com.bytebazaar.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity @Table(name = "settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Setting {
    @Id @Column(name = "setting_key") private String key;
    @Column(name = "setting_value") private String value;
}
