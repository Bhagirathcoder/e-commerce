package com.bytebazaar.service;

import com.bytebazaar.dto.Dtos.*;
import com.bytebazaar.entity.*;
import com.bytebazaar.exception.*;
import com.bytebazaar.repository.*;
import com.bytebazaar.util.AppConstants;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderService {
    private static final List<OrderStatus> FLOW = List.of(OrderStatus.PLACED, OrderStatus.CONFIRMED, OrderStatus.PROCESSING,
            OrderStatus.SHIPPING, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED);

    private final OrderRepository orders;
    private final ProductRepository products;
    private final CartItemRepository carts;
    private final OrderTrackingRepository trackingRepo;
    private final PaymentRepository payments;
    private final SellerCommissionRepository commissions;
    private final ReturnRequestRepository returns;
    private final ReviewRepository reviews;
    private final RefundRepository refunds;
    private final PromotionService promos;
    private final SettingService settings;
    private final NotificationService notif;

    // ====================================================================== place order
    /** One transaction: lock rows -> validate stock -> order + items -> payment -> stock reduced -> tracking -> notifications. */
    public OrderDto place(User user, OrderReq r) {
        Map<Long, Integer> lines = new TreeMap<>();            // sorted ids => consistent lock order (no deadlocks)
        boolean buyNow = r.productId() != null;
        if (buyNow) {
            int q = r.quantity() == null ? 1 : r.quantity();
            if (q < 1 || q > 10) throw new InvalidRequestException("Quantity must be between 1 and 10");
            lines.put(r.productId(), q);
        } else {
            for (CartItem ci : carts.findByUserOrderByIdAsc(user)) lines.merge(ci.getProduct().getId(), ci.getQuantity(), Integer::sum);
        }
        if (lines.isEmpty()) throw new InvalidRequestException("Your cart is empty");

        Map<Long, Integer> live = promos.liveDiscountMap();
        Order o = new Order();
        o.setUser(user);
        o.setPaymentMethod(r.paymentMethod());
        o.setShipName(r.shipName()); o.setShipPhone(r.shipPhone()); o.setShipAddress(r.shipAddress());
        o.setShipCity(r.shipCity()); o.setShipDistrict(r.shipDistrict()); o.setShipState(r.shipState()); o.setShipPin(r.shipPin());
        o.setEstimatedDelivery(LocalDate.now().plusDays(5));

        BigDecimal subtotal = BigDecimal.ZERO, net = BigDecimal.ZERO;
        Set<User> sellers = new LinkedHashSet<>();
        for (Map.Entry<Long, Integer> e : lines.entrySet()) {
            final Long pid = e.getKey();
            int qty = e.getValue();
            Product p = products.lockById(pid).orElseThrow(() -> new ProductNotFoundException(pid));
            if (p.isDeleted() || !p.isActive() || p.getVerification() != VerificationStatus.VERIFIED)
                throw new InvalidRequestException("\"" + p.getName() + "\" is no longer available");
            if (p.getSeller().getStatus() == AccountStatus.BLOCKED)
                throw new InvalidRequestException("\"" + p.getName() + "\" is currently unavailable");
            if (p.getStock() < qty) throw new InsufficientStockException(p.getName(), p.getStock());

            BigDecimal unit = p.finalPrice(ProductMapper.effectiveDiscount(p, live));
            OrderItem it = new OrderItem();
            it.setOrder(o); it.setProduct(p); it.setSeller(p.getSeller());
            it.setProductName(p.getName()); it.setImageUrl(p.getImages().isEmpty() ? null : p.getImages().get(0));
            it.setQuantity(qty); it.setMrp(p.getPrice()); it.setUnitPrice(unit);
            it.setReturnable(p.isReturnable()); it.setWarrantyMonths(p.getWarrantyMonths());
            o.getItems().add(it);
            sellers.add(p.getSeller());

            subtotal = subtotal.add(p.getPrice().multiply(BigDecimal.valueOf(qty)));
            net = net.add(unit.multiply(BigDecimal.valueOf(qty)));
            p.setStock(p.getStock() - qty);                    // stock reduced inside the same transaction
            p.setSoldCount(p.getSoldCount() + qty);
        }
        BigDecimal shipping = net.compareTo(AppConstants.FREE_SHIPPING_ABOVE) >= 0 ? BigDecimal.ZERO : AppConstants.SHIPPING_FEE;
        BigDecimal tax = net.multiply(AppConstants.TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        o.setSubtotal(subtotal); o.setDiscount(subtotal.subtract(net)); o.setShipping(shipping); o.setTax(tax);
        o.setTotal(net.add(shipping).add(tax));
        o = orders.save(o);
        o.setInvoiceNumber("BB-INV-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + o.getId());

        Payment pay = new Payment();
        pay.setOrder(o); pay.setMethod(r.paymentMethod()); pay.setAmount(o.getTotal());
        pay.setStatus(PaymentStatus.PENDING);
        if (r.paymentMethod() == PaymentMethod.UPI) pay.setGatewayRef("GW-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        payments.save(pay);

        addTracking(o, OrderStatus.PLACED, "ByteBazaar", r.paymentMethod() == PaymentMethod.COD
                ? "Order placed. Pay cash on delivery." : "Order placed. Waiting for UPI payment.");
        if (!buyNow) carts.deleteByUser(user);

        notif.notifyAndEmail(user, NotificationType.ORDER, "Order #" + o.getId() + " placed",
                "Your order #" + o.getId() + " has been placed" + (r.paymentMethod() == PaymentMethod.UPI ? ". Please complete the UPI payment to confirm it." : "."));
        if (r.paymentMethod() == PaymentMethod.COD) notifySellersAndAdmin(o, sellers);
        return toDto(o, user, null);
    }

    void notifySellersAndAdmin(Order o, Collection<User> sellers) {
        for (User s : sellers) notif.notify(s, NotificationType.ORDER, "New order #" + o.getId() + " received for your product(s).");
        notif.notifyAdmins(NotificationType.ORDER, "New order #" + o.getId() + " (Rs. " + o.getTotal() + ", " + o.getPaymentMethod() + ").");
    }

    Set<User> sellersOf(Order o) { return o.getItems().stream().map(OrderItem::getSeller).collect(Collectors.toCollection(LinkedHashSet::new)); }

    // ====================================================================== status changes
    public void addTracking(Order o, OrderStatus s, String location, String description) {
        OrderTracking t = new OrderTracking();
        t.setOrder(o); t.setStatus(s); t.setLocation(location); t.setDescription(description);
        trackingRepo.save(t);
    }

    /** Seller updates only orders that contain their products. */
    public OrderDto sellerUpdate(User seller, Long orderId, StatusReq r) {
        Order o = orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException("Order not found"));
        if (o.getItems().stream().noneMatch(i -> i.getSeller().getId().equals(seller.getId())))
            throw new ForbiddenException("This order does not contain your products");
        if (r.status() == OrderStatus.CANCELLED || r.status() == OrderStatus.PLACED)
            throw new InvalidRequestException("Sellers can set: CONFIRMED, PROCESSING, SHIPPING, OUT_FOR_DELIVERY, DELIVERED");
        return toDto(applyStatus(o, r.status(), r.location(), r.description()), seller, seller.getId());
    }

    public OrderDto adminUpdate(Long orderId, StatusReq r) {
        Order o = orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException("Order not found"));
        if (r.status() == OrderStatus.CANCELLED) { cancelInternal(o, "Cancelled by ByteBazaar admin"); return toDto(o, null, null); }
        return toDto(applyStatus(o, r.status(), r.location(), r.description()), null, null);
    }

    private Order applyStatus(Order o, OrderStatus ns, String location, String description) {
        if (o.getStatus() == OrderStatus.CANCELLED || o.getStatus() == OrderStatus.DELIVERED)
            throw new InvalidRequestException("This order is already " + o.getStatus().name().toLowerCase());
        if (o.getPaymentMethod() == PaymentMethod.UPI && o.getPaymentStatus() != PaymentStatus.SUCCESS)
            throw new InvalidRequestException("Waiting for the customer's UPI payment - the order cannot be processed yet");
        int cur = FLOW.indexOf(o.getStatus()), next = FLOW.indexOf(ns);
        if (next < 0 || next <= cur) throw new InvalidRequestException("Invalid status change: " + o.getStatus() + " -> " + ns);

        o.setStatus(ns);
        addTracking(o, ns, location == null || location.isBlank() ? "ByteBazaar Logistics" : location,
                description == null || description.isBlank() ? defaultDescription(ns) : description);

        User cust = o.getUser();
        switch (ns) {
            case CONFIRMED -> notif.notifyAndEmail(cust, NotificationType.ORDER, "Order #" + o.getId() + " confirmed", "Your order #" + o.getId() + " has been confirmed.");
            case PROCESSING -> notif.notify(cust, NotificationType.ORDER, "Your order #" + o.getId() + " is being prepared.");
            case SHIPPING -> notif.notifyAndEmail(cust, NotificationType.SHIPPING, "Order #" + o.getId() + " shipped", "Your order #" + o.getId() + " has been shipped.");
            case OUT_FOR_DELIVERY -> notif.notifyAndEmail(cust, NotificationType.SHIPPING, "Order #" + o.getId() + " out for delivery", "Your order #" + o.getId() + " is out for delivery.");
            case DELIVERED -> {
                o.setDeliveredAt(LocalDateTime.now());          // starts the 7-day return window (checked on the backend)
                if (o.getPaymentMethod() == PaymentMethod.COD) {
                    o.setPaymentStatus(PaymentStatus.SUCCESS);
                    payments.findFirstByOrderOrderByIdDesc(o).ifPresent(p -> { p.setStatus(PaymentStatus.SUCCESS); p.setVerifiedAt(LocalDateTime.now()); });
                }
                createCommissions(o);
                notif.notifyAndEmail(cust, NotificationType.DELIVERY, "Order #" + o.getId() + " delivered",
                        "Your order #" + o.getId() + " has been delivered. You can return eligible items until "
                                + o.getDeliveredAt().toLocalDate().plusDays(AppConstants.RETURN_DAYS) + ". Your invoice is ready to download.");
            }
            default -> { }
        }
        return o;
    }

    private String defaultDescription(OrderStatus s) {
        return switch (s) {
            case CONFIRMED -> "Seller confirmed your order";
            case PROCESSING -> "Your item is being packed";
            case SHIPPING -> "Shipment dispatched from seller warehouse";
            case OUT_FOR_DELIVERY -> "Delivery partner is on the way";
            case DELIVERED -> "Delivered to the customer";
            default -> s.name();
        };
    }

    /** Sale completed -> commission calculated on the backend per order item. */
    private void createCommissions(Order o) {
        BigDecimal pct = settings.commissionPercent();
        for (OrderItem i : o.getItems()) {
            BigDecimal sale = i.lineTotal();
            BigDecimal comm = sale.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            SellerCommission c = new SellerCommission();
            c.setSeller(i.getSeller()); c.setOrder(o); c.setOrderItem(i); c.setProduct(i.getProduct());
            c.setSaleAmount(sale); c.setCommissionPct(pct); c.setCommissionAmount(comm); c.setSellerAmount(sale.subtract(comm));
            commissions.save(c);
        }
    }

    // ====================================================================== cancel
    public OrderDto cancel(User user, Long orderId) {
        Order o = owned(user, orderId);
        if (o.getStatus() != OrderStatus.PLACED && o.getStatus() != OrderStatus.CONFIRMED)
            throw new InvalidRequestException("This order can no longer be cancelled. Once it is processed, please use the return option after delivery.");
        cancelInternal(o, "Cancelled by customer");
        return toDto(o, user, null);
    }

    private void cancelInternal(Order o, String reason) {
        if (o.getStatus() == OrderStatus.CANCELLED || o.getStatus() == OrderStatus.DELIVERED)
            throw new InvalidRequestException("This order cannot be cancelled");
        for (OrderItem i : o.getItems()) {
            products.lockById(i.getProduct().getId()).ifPresent(p -> {
                p.setStock(p.getStock() + i.getQuantity());
                p.setSoldCount(Math.max(0, p.getSoldCount() - i.getQuantity()));
            });
        }
        o.setStatus(OrderStatus.CANCELLED);
        addTracking(o, OrderStatus.CANCELLED, "ByteBazaar", reason);
        if (o.getPaymentStatus() == PaymentStatus.SUCCESS) {          // prepaid -> simulated refund to source
            o.setPaymentStatus(PaymentStatus.REFUNDED);
            Refund r = new Refund();
            r.setOrder(o); r.setUser(o.getUser()); r.setAmount(o.getTotal()); r.setStatus(RefundStatus.COMPLETED);
            r.setDestination("Original payment method (UPI)"); r.setCompletedAt(LocalDateTime.now());
            refunds.save(r);
            notif.notifyAndEmail(o.getUser(), NotificationType.REFUND, "Refund for order #" + o.getId(),
                    "Your order #" + o.getId() + " was cancelled and Rs. " + o.getTotal() + " has been refunded to your original payment method.");
        } else {
            o.setPaymentStatus(PaymentStatus.FAILED);
            notif.notify(o.getUser(), NotificationType.ORDER, "Your order #" + o.getId() + " has been cancelled.");
        }
        for (User s : sellersOf(o)) notif.notify(s, NotificationType.ORDER, "Order #" + o.getId() + " was cancelled.");
    }

    /** Frees stock of UPI orders that were never paid within the allowed time. */
    public int cancelUnpaidExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(AppConstants.UNPAID_UPI_MINUTES);
        List<Order> stale = orders.findByPaymentMethodAndPaymentStatusAndStatusAndCreatedAtBefore(
                PaymentMethod.UPI, PaymentStatus.PENDING, OrderStatus.PLACED, cutoff);
        for (Order o : stale) cancelInternal(o, "Auto-cancelled: UPI payment not completed in " + AppConstants.UNPAID_UPI_MINUTES + " minutes");
        return stale.size();
    }

    // ====================================================================== queries
    @Transactional(readOnly = true)
    public List<OrderDto> mine(User u) { return orders.findByUserOrderByCreatedAtDesc(u).stream().map(o -> toDto(o, u, null)).toList(); }

    @Transactional(readOnly = true)
    public OrderDto get(User u, Long id) { return toDto(owned(u, id), u, null); }

    @Transactional(readOnly = true)
    public List<TrackingDto> tracking(User u, Long id) {
        Order o = orders.findById(id).orElseThrow(() -> new OrderNotFoundException("Order not found"));
        boolean ok = u.getRole() == Role.ADMIN || o.getUser().getId().equals(u.getId())
                || (u.getRole() == Role.SELLER && o.getItems().stream().anyMatch(i -> i.getSeller().getId().equals(u.getId())));
        if (!ok) throw new ForbiddenException("You cannot view this order");
        return trackingRepo.findByOrderOrderByEventTimeAscIdAsc(o).stream()
                .map(t -> new TrackingDto(t.getStatus(), t.getEventTime(), t.getLocation(), t.getDescription())).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderDto> forSeller(User seller) { return orders.findBySeller(seller.getId()).stream().map(o -> toDto(o, seller, seller.getId())).toList(); }

    @Transactional(readOnly = true)
    public List<OrderDto> all() { return orders.findAllByOrderByCreatedAtDesc().stream().map(o -> toDto(o, null, null)).toList(); }

    /** Order if it belongs to the user - otherwise 403 (users can never read each other's orders). */
    Order owned(User u, Long id) {
        Order o = orders.findById(id).orElseThrow(() -> new OrderNotFoundException("Order not found"));
        if (!o.getUser().getId().equals(u.getId())) throw new ForbiddenException("You cannot access this order");
        return o;
    }

    /** Order visible to owner, admin, or a seller with items in it (used by PDF endpoints). */
    @Transactional(readOnly = true)
    public Order viewable(User u, Long id) {
        Order o = orders.findById(id).orElseThrow(() -> new OrderNotFoundException("Order not found"));
        boolean ok = u.getRole() == Role.ADMIN || o.getUser().getId().equals(u.getId())
                || (u.getRole() == Role.SELLER && o.getItems().stream().anyMatch(i -> i.getSeller().getId().equals(u.getId())));
        if (!ok) throw new ForbiddenException("You cannot access this order");
        return o;
    }

    public static LocalDateTime returnDeadline(Order o) {
        return o.getDeliveredAt() == null ? null : o.getDeliveredAt().plusDays(AppConstants.RETURN_DAYS);
    }

    public OrderDto toDto(Order o, User viewer, Long sellerFilter) {
        LocalDateTime deadline = returnDeadline(o);
        boolean open = o.getStatus() == OrderStatus.DELIVERED && deadline != null && !LocalDateTime.now().isAfter(deadline);
        List<OrderItemDto> items = new ArrayList<>();
        boolean isOwner = viewer != null && o.getUser().getId().equals(viewer.getId());
        for (OrderItem i : o.getItems()) {
            if (sellerFilter != null && !i.getSeller().getId().equals(sellerFilter)) continue;
            List<ReturnRequest> rr = returns.findByOrderItemOrderByIdDesc(i);
            ReturnRequest latest = rr.isEmpty() ? null : rr.get(0);
            boolean active = rr.stream().anyMatch(x -> x.getStatus() != ReturnStatus.REJECTED);
            boolean canReturn = isOwner && open && i.isReturnable() && !active;
            boolean reviewed = isOwner && reviews.existsByUserAndProduct(viewer, i.getProduct());
            items.add(new OrderItemDto(i.getId(), i.getProduct().getId(), i.getProductName(), i.getImageUrl(), i.getSeller().getId(),
                    i.getSeller().getStoreName() != null ? i.getSeller().getStoreName() : i.getSeller().getFullName(),
                    i.getQuantity(), i.getMrp(), i.getUnitPrice(), i.lineTotal(), i.isReturnable(), i.getWarrantyMonths(),
                    canReturn, latest == null ? null : latest.getStatus(), latest == null ? null : latest.getId(), reviewed));
        }
        boolean canCancel = isOwner && (o.getStatus() == OrderStatus.PLACED || o.getStatus() == OrderStatus.CONFIRMED);
        User c = o.getUser();
        return new OrderDto(o.getId(), o.getInvoiceNumber(), o.getStatus(), o.getPaymentMethod(), o.getPaymentStatus(), items,
                o.getSubtotal(), o.getDiscount(), o.getShipping(), o.getTax(), o.getTotal(),
                c.getFullName(), c.getEmail(), c.getPhone(),
                o.getShipName(), o.getShipPhone(), o.getShipAddress(), o.getShipCity(), o.getShipDistrict(), o.getShipState(), o.getShipPin(),
                o.getCreatedAt(), o.getEstimatedDelivery(), o.getDeliveredAt(), deadline, open, canCancel);
    }
}
