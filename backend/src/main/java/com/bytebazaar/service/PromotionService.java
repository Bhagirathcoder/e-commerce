package com.bytebazaar.service;

import com.bytebazaar.dto.Dtos.*;
import com.bytebazaar.entity.*;
import com.bytebazaar.exception.NotFoundException;
import com.bytebazaar.repository.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Festival sales & brand deals. "Live" is evaluated from start/end dates on every call, so sales activate/expire automatically. */
@Service
@RequiredArgsConstructor
public class PromotionService {
    private final PromotionRepository repo;
    private final ProductRepository products;

    /** productId -> best live promotion discount */
    @Transactional(readOnly = true)
    public Map<Long, Integer> liveDiscountMap() {
        Map<Long, Integer> m = new HashMap<>();
        for (Promotion p : repo.findByEnabledTrue()) {
            if (!p.isLive()) continue;
            for (Product pr : p.getProducts()) m.merge(pr.getId(), p.getDiscountPercent(), Math::max);
        }
        return m;
    }

    @Transactional(readOnly = true)
    public List<PromotionDto> live() {
        return repo.findByEnabledTrue().stream().filter(Promotion::isLive).map(p -> toDto(p, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<PromotionDto> all() { return repo.findAllByOrderByIdDesc().stream().map(p -> toDto(p, false)).toList(); }

    @Transactional
    public PromotionDto save(Long id, PromotionReq r) {
        Promotion p = id == null ? new Promotion() : repo.findById(id).orElseThrow(() -> new NotFoundException("Promotion not found"));
        if (r.endDate().isBefore(r.startDate())) throw new com.bytebazaar.exception.InvalidRequestException("End date must be after start date");
        p.setType(r.type()); p.setName(r.name()); p.setBrandName(r.brandName()); p.setDescription(r.description());
        p.setBannerUrl(r.bannerUrl()); p.setDiscountPercent(r.discountPercent());
        p.setStartDate(r.startDate()); p.setEndDate(r.endDate()); p.setEnabled(r.enabled());
        Set<Product> set = new HashSet<>();
        if (r.productIds() != null) for (Long pid : r.productIds()) products.findById(pid).ifPresent(set::add);
        p.setProducts(set);
        return toDto(repo.save(p), false);
    }

    @Transactional
    public void delete(Long id) { repo.deleteById(id); }

    private PromotionDto toDto(Promotion p, boolean withProducts) {
        List<Long> ids = p.getProducts().stream().map(Product::getId).sorted().collect(Collectors.toList());
        return new PromotionDto(p.getId(), p.getType(), p.getName(), p.getBrandName(), p.getDescription(), p.getBannerUrl(),
                p.getDiscountPercent(), p.getStartDate(), p.getEndDate(), p.isEnabled(), p.isLive(), ids, null);
    }
}
