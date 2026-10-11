package com.bytebazaar.service;

import com.bytebazaar.entity.Setting;
import com.bytebazaar.repository.SettingRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettingService {
    public static final String COMMISSION = "commission.percent";
    private final SettingRepository repo;

    public BigDecimal commissionPercent() {
        return repo.findById(COMMISSION).map(s -> new BigDecimal(s.getValue())).orElse(new BigDecimal("10"));
    }
    public void setCommissionPercent(BigDecimal p) { repo.save(new Setting(COMMISSION, p.toPlainString())); }
}
