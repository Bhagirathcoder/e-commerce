package com.bytebazaar.service;

import com.bytebazaar.entity.*;
import com.bytebazaar.repository.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sales, revenue and stock reports. sellerId == null => whole marketplace (admin); otherwise only that seller's data. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {
    private final OrderItemRepository items;
    private final ProductRepository products;
    private final SellerCommissionRepository commissions;
    private final RefundRepository refunds;
    private final PromotionService promos;

    public Map<String, Object> report(Long sellerId, int days) {
        days = Math.max(1, Math.min(days, 365));
        LocalDate today = LocalDate.now();
        List<OrderItem> list = items.findForAnalytics(today.minusDays(days - 1L).atStartOfDay(), sellerId);

        BigDecimal revenue = BigDecimal.ZERO, deliveredRev = BigDecimal.ZERO, pendingRev = BigDecimal.ZERO;
        long units = 0;
        Set<Long> orderIds = new HashSet<>();
        Map<String, BigDecimal> revDay = new LinkedHashMap<>();
        Map<String, Long> unitsDay = new LinkedHashMap<>(), ordersDay = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) { String d = today.minusDays(i).toString(); revDay.put(d, BigDecimal.ZERO); unitsDay.put(d, 0L); ordersDay.put(d, 0L); }
        Map<Long, Object[]> byProduct = new HashMap<>();          // id -> {name, units, revenue}
        Map<String, BigDecimal> byCategory = new TreeMap<>();
        Map<String, BigDecimal> bySeller = new HashMap<>();
        Map<String, BigDecimal> byPayment = new TreeMap<>();
        Map<String, Set<Long>> dayOrders = new HashMap<>();

        for (OrderItem i : list) {
            BigDecimal line = i.lineTotal();
            Order o = i.getOrder();
            revenue = revenue.add(line); units += i.getQuantity(); orderIds.add(o.getId());
            if (o.getStatus() == OrderStatus.DELIVERED) deliveredRev = deliveredRev.add(line); else pendingRev = pendingRev.add(line);
            String d = o.getCreatedAt().toLocalDate().toString();
            revDay.computeIfPresent(d, (k, v) -> v.add(line));
            unitsDay.computeIfPresent(d, (k, v) -> v + i.getQuantity());
            dayOrders.computeIfAbsent(d, k -> new HashSet<>()).add(o.getId());
            Object[] bp = byProduct.computeIfAbsent(i.getProduct().getId(), k -> new Object[]{i.getProductName(), 0L, BigDecimal.ZERO});
            bp[1] = (Long) bp[1] + i.getQuantity(); bp[2] = ((BigDecimal) bp[2]).add(line);
            Category c = i.getProduct().getCategory();
            byCategory.merge(c.getParent() != null ? c.getParent().getName() : c.getName(), line, BigDecimal::add);
            byPayment.merge(o.getPaymentMethod().name(), line, BigDecimal::add);
            bySeller.merge(i.getSeller().getStoreName() != null ? i.getSeller().getStoreName() : i.getSeller().getFullName(), line, BigDecimal::add);
        }
        dayOrders.forEach((d, s) -> ordersDay.computeIfPresent(d, (k, v) -> (long) s.size()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", days);
        Map<String, Object> sum = new LinkedHashMap<>();
        sum.put("revenue", revenue);
        sum.put("orders", orderIds.size());
        sum.put("unitsSold", units);
        sum.put("avgOrderValue", orderIds.isEmpty() ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(orderIds.size()), 2, RoundingMode.HALF_UP));
        sum.put("deliveredRevenue", deliveredRev);
        sum.put("pendingRevenue", pendingRev);
        sum.put("commission", commissions.sumCommission(sellerId));
        sum.put("netEarnings", commissions.sumSellerAmount(sellerId));
        if (sellerId == null) {
            BigDecimal refunded = refunds.findAll().stream().map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            sum.put("refundedAmount", refunded);
        }
        out.put("summary", sum);

        List<Map<String, Object>> daily = new ArrayList<>();
        revDay.forEach((d, r) -> daily.add(Map.of("date", d, "revenue", r, "units", unitsDay.get(d), "orders", ordersDay.get(d))));
        out.put("daily", daily);

        out.put("topProducts", byProduct.entrySet().stream()
                .sorted((a, b) -> ((BigDecimal) b.getValue()[2]).compareTo((BigDecimal) a.getValue()[2])).limit(8)
                .map(e -> Map.of("productId", e.getKey(), "name", e.getValue()[0], "units", e.getValue()[1], "revenue", e.getValue()[2])).toList());
        out.put("revenueByCategory", byCategory);
        out.put("revenueByPayment", byPayment);
        if (sellerId == null) out.put("revenueBySeller", bySeller.entrySet().stream().sorted((a, b) -> b.getValue().compareTo(a.getValue())).limit(8)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new)));

        // ---- stock
        List<Product> ps = sellerId == null ? products.findByDeletedFalseOrderByIdDesc()
                : products.findBySellerAndDeletedFalseOrderByIdDesc(sellerRef(sellerId));
        Map<Long, Integer> live = promos.liveDiscountMap();
        long totalUnits = 0, out0 = 0, low = 0; BigDecimal value = BigDecimal.ZERO;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Product p : ps) {
            BigDecimal unit = p.finalPrice(ProductMapper.effectiveDiscount(p, live));
            BigDecimal val = unit.multiply(BigDecimal.valueOf(p.getStock()));
            totalUnits += p.getStock(); value = value.add(val);
            String st = p.getStock() == 0 ? "OUT_OF_STOCK" : p.getStock() <= 5 ? "LOW_STOCK" : "IN_STOCK";
            if (p.getStock() == 0) out0++; else if (p.getStock() <= 5) low++;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", p.getId()); r.put("name", p.getName()); r.put("sku", p.getSku()); r.put("category", p.getCategory().getName());
            r.put("stock", p.getStock()); r.put("sold", p.getSoldCount()); r.put("unitPrice", unit); r.put("stockValue", val); r.put("status", st);
            r.put("verification", p.getVerification()); r.put("active", p.isActive());
            rows.add(r);
        }
        rows.sort(Comparator.comparingInt(r -> (Integer) r.get("stock")));
        Map<String, Object> stock = new LinkedHashMap<>();
        stock.put("products", ps.size()); stock.put("totalUnits", totalUnits); stock.put("stockValue", value);
        stock.put("outOfStock", out0); stock.put("lowStock", low); stock.put("items", rows);
        out.put("stock", stock);
        return out;
    }

    /** Lightweight reference carrying only the id (enough for the "seller = ?" query). */
    private User sellerRef(Long id) { User u = new User(); u.setId(id); return u; }
}
