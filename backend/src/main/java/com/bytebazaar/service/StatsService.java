package com.bytebazaar.service;

import com.bytebazaar.dto.Dtos.*;
import com.bytebazaar.entity.*;
import com.bytebazaar.exception.*;
import com.bytebazaar.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dashboards + admin management of users/sellers/commissions. */
@Service
@RequiredArgsConstructor
@Transactional
public class StatsService {
    private final UserRepository users;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final ReturnRequestRepository returns;
    private final RefundRepository refunds;
    private final SellerCommissionRepository commissions;
    private final ContactMessageRepository contacts;
    private final SupportMessageRepository support;
    private final SettingService settings;
    private final NotificationService notif;

    // ---------------------------------------------------------------- dashboards
    @Transactional(readOnly = true)
    public Map<String, Object> sellerDashboard(User s) {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Product> mine = products.findBySellerAndDeletedFalseOrderByIdDesc(s);
        m.put("totalProducts", mine.size());
        m.put("activeProducts", mine.stream().filter(p -> p.isActive() && p.getVerification() == VerificationStatus.VERIFIED).count());
        m.put("pendingProducts", mine.stream().filter(p -> p.getVerification() == VerificationStatus.PENDING_VERIFICATION).count());
        m.put("outOfStock", mine.stream().filter(p -> p.getStock() == 0).count());
        m.put("totalOrders", orders.countBySeller(s.getId()));
        long delivered = orders.countBySellerAndStatus(s.getId(), OrderStatus.DELIVERED);
        long cancelled = orders.countBySellerAndStatus(s.getId(), OrderStatus.CANCELLED);
        m.put("deliveredOrders", delivered);
        m.put("pendingOrders", orders.countBySeller(s.getId()) - delivered - cancelled);
        m.put("totalSales", commissions.sumSales(s.getId()));
        m.put("commission", commissions.sumCommission(s.getId()));
        m.put("netEarnings", commissions.sumSellerAmount(s.getId()));
        m.put("paidOut", commissions.sumPaidOut(s.getId()));
        m.put("commissionPercent", settings.commissionPercent());
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminDashboard() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalUsers", users.countByRole(Role.USER));
        m.put("totalSellers", users.countByRole(Role.SELLER));
        m.put("totalProducts", products.countByDeletedFalse());
        m.put("verifiedProducts", products.countByVerificationAndDeletedFalse(VerificationStatus.VERIFIED));
        m.put("pendingProducts", products.countByVerificationAndDeletedFalse(VerificationStatus.PENDING_VERIFICATION));
        m.put("totalOrders", orders.count());
        m.put("totalSales", orders.totalSales());
        m.put("totalCommission", commissions.sumCommission(null));
        m.put("sellerPayout", commissions.sumPaidOut(null));
        m.put("pendingCommission", commissions.sumPendingCommission(null));
        m.put("pendingOrders", orders.countByStatus(OrderStatus.PLACED) + orders.countByStatus(OrderStatus.CONFIRMED)
                + orders.countByStatus(OrderStatus.PROCESSING) + orders.countByStatus(OrderStatus.SHIPPING) + orders.countByStatus(OrderStatus.OUT_FOR_DELIVERY));
        m.put("deliveredOrders", orders.countByStatus(OrderStatus.DELIVERED));
        m.put("returnRequests", returns.count());
        m.put("refunds", refunds.count());
        m.put("supportTickets", contacts.countByStatus("OPEN") + support.countBySenderAndSeenFalse(SupportSender.USER));
        m.put("commissionPercent", settings.commissionPercent());

        // chart data
        List<Order> all = orders.findAllByOrderByCreatedAtDesc();
        Map<String, BigDecimal> salesByDay = new LinkedHashMap<>();
        Map<String, Long> ordersByDay = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) { String d = LocalDate.now().minusDays(i).toString(); salesByDay.put(d, BigDecimal.ZERO); ordersByDay.put(d, 0L); }
        for (Order o : all) {
            String d = o.getCreatedAt().toLocalDate().toString();
            if (salesByDay.containsKey(d) && o.getStatus() != OrderStatus.CANCELLED) salesByDay.merge(d, o.getTotal(), BigDecimal::add);
            if (ordersByDay.containsKey(d)) ordersByDay.merge(d, 1L, Long::sum);
        }
        Map<String, Object> charts = new LinkedHashMap<>();
        charts.put("salesByDay", salesByDay);
        charts.put("ordersByDay", ordersByDay);
        charts.put("ordersByStatus", all.stream().collect(Collectors.groupingBy(o -> o.getStatus().name(), TreeMap::new, Collectors.counting())));
        Map<String, Long> prod = new LinkedHashMap<>();
        for (VerificationStatus v : VerificationStatus.values()) prod.put(v.name(), products.countByVerificationAndDeletedFalse(v));
        charts.put("productsByStatus", prod);
        Map<String, Long> usr = new LinkedHashMap<>();
        for (Role r : Role.values()) usr.put(r.name(), users.countByRole(r));
        charts.put("usersByRole", usr);
        Map<String, Long> ret = new LinkedHashMap<>();
        for (ReturnStatus r : ReturnStatus.values()) ret.put(r.name(), returns.countByStatus(r));
        charts.put("returnsByStatus", ret);
        Map<String, Object> comm = new LinkedHashMap<>();
        comm.put("commission", commissions.sumCommission(null));
        comm.put("sellerAmount", commissions.sumSellerAmount(null));
        charts.put("commission", comm);
        m.put("charts", charts);
        return m;
    }

    // ---------------------------------------------------------------- admin: users & sellers
    @Transactional(readOnly = true)
    public List<AdminUserDto> listByRole(Role role) {
        return users.findByRoleOrderByCreatedAtDesc(role).stream().map(u -> new AdminUserDto(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(),
                u.getRole(), u.getStatus(), role == Role.SELLER ? orders.countBySeller(u.getId()) : orders.countByUser(u),
                role == Role.SELLER ? products.countBySellerAndDeletedFalse(u) : 0L,
                role == Role.SELLER ? orderItems.salesBySeller(u.getId()) : BigDecimal.ZERO,
                role == Role.SELLER ? commissions.sumCommission(u.getId()) : BigDecimal.ZERO, u.getCreatedAt())).toList();
    }

    public void setStatus(Long id, Role expected, AccountStatus status, User actor) {
        User u = users.findById(id).orElseThrow(() -> expected == Role.SELLER ? new SellerNotFoundException("Seller not found") : new UserNotFoundException("User not found"));
        if (u.getRole() != expected) throw new InvalidRequestException("Wrong account type for this action");
        if (u.getId().equals(actor.getId())) throw new InvalidRequestException("You cannot block your own account");
        u.setStatus(status);
        notif.notify(u, NotificationType.ACCOUNT, status == AccountStatus.BLOCKED
                ? "Your ByteBazaar account has been blocked by the administrator." : "Your ByteBazaar account has been unblocked.");
    }

    public void deleteAccount(Long id, User actor) {
        User u = users.findById(id).orElseThrow(() -> new UserNotFoundException("Account not found"));
        if (u.getRole() == Role.ADMIN) throw new ForbiddenException("Admin accounts cannot be deleted");
        if (u.getId().equals(actor.getId())) throw new InvalidRequestException("You cannot delete your own account");
        if (orders.countByUser(u) > 0 || orders.countBySeller(u.getId()) > 0 || products.countBySellerAndDeletedFalse(u) > 0)
            throw new ConflictException("This account has order/product history and cannot be deleted. Block it instead.");
        try { users.delete(u); users.flush(); }
        catch (Exception e) { throw new ConflictException("This account is linked to other records. Block it instead."); }
    }

    public UserDto adminEdit(Long id, ProfileReq r) {
        User u = users.findById(id).orElseThrow(() -> new UserNotFoundException("Account not found"));
        if (!r.email().equalsIgnoreCase(u.getEmail()) && users.existsByEmailIgnoreCase(r.email())) throw new ConflictException("Email already in use");
        if (!r.phone().equals(u.getPhone()) && users.existsByPhone(r.phone())) throw new ConflictException("Phone already in use");
        u.setFullName(r.fullName()); u.setEmail(r.email().toLowerCase()); u.setPhone(r.phone());
        u.setAddress(r.address()); u.setCity(r.city()); u.setState(r.state()); u.setPin(r.pin());
        if (r.storeName() != null && !r.storeName().isBlank()) u.setStoreName(r.storeName());
        return AuthService.toDto(u);
    }

    // ---------------------------------------------------------------- commissions
    private CommissionDto dto(SellerCommission c) {
        return new CommissionDto(c.getId(), c.getSeller().getId(), c.getSeller().getStoreName() != null ? c.getSeller().getStoreName() : c.getSeller().getFullName(),
                c.getOrder().getId(), c.getProduct().getId(), c.getOrderItem().getProductName(), c.getSaleAmount(), c.getCommissionPct(),
                c.getCommissionAmount(), c.getSellerAmount(), c.getPaymentStatus(), c.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<CommissionDto> commissionsFor(User seller) { return commissions.findBySellerOrderByCreatedAtDesc(seller).stream().map(this::dto).toList(); }

    @Transactional(readOnly = true)
    public List<CommissionDto> allCommissions() { return commissions.findAllByOrderByCreatedAtDesc().stream().map(this::dto).toList(); }

    public CommissionDto markPaid(Long id) {
        SellerCommission c = commissions.findById(id).orElseThrow(() -> new NotFoundException("Commission record not found"));
        if (c.getPaymentStatus() != CommissionStatus.PENDING) throw new InvalidRequestException("Only pending records can be marked paid");
        c.setPaymentStatus(CommissionStatus.PAID);
        notif.notify(c.getSeller(), NotificationType.PAYMENT, "Rs. " + c.getSellerAmount() + " for order #" + c.getOrder().getId() + " has been paid out to your bank account.");
        return dto(c);
    }

    public void setCommissionPercent(BigDecimal p) { settings.setCommissionPercent(p); }
}
