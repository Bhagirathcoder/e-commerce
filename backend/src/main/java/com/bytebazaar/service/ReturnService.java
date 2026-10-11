package com.bytebazaar.service;

import com.bytebazaar.dto.Dtos.*;
import com.bytebazaar.entity.*;
import com.bytebazaar.exception.*;
import com.bytebazaar.repository.*;
import com.bytebazaar.util.AppConstants;
import com.bytebazaar.util.MaskUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Return -> approval -> pickup -> received -> analysis -> refund. The 7-day window is calculated here from the
 * stored delivery timestamp; the browser is never trusted for it.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ReturnService {
    private static final Set<String> REASONS = Set.of("Damaged Product", "Wrong Product", "Defective Product",
            "Product Not as Described", "Size Problem", "Missing Item", "Other");

    private final ReturnRequestRepository returns;
    private final ReturnTrackingRepository trackingRepo;
    private final OrderItemRepository items;
    private final RefundRepository refunds;
    private final BankAccountRepository banks;
    private final SellerCommissionRepository commissions;
    private final ProductRepository products;
    private final NotificationService notif;
    private final FileStorageService files;

    // ------------------------------------------------------------------ customer
    public ReturnDto request(User u, Long orderItemId, String reason, String comment, List<org.springframework.web.multipart.MultipartFile> photos) {
        OrderItem item = items.findById(orderItemId).orElseThrow(() -> new NotFoundException("Order item not found"));
        Order o = item.getOrder();
        if (!o.getUser().getId().equals(u.getId())) throw new ForbiddenException("You cannot return this item");
        if (o.getStatus() != OrderStatus.DELIVERED || o.getDeliveredAt() == null)
            throw new InvalidRequestException("Only delivered items can be returned");
        if (!item.isReturnable()) throw new InvalidRequestException("This product is not eligible for return");
        if (LocalDateTime.now().isAfter(OrderService.returnDeadline(o)))
            throw new ReturnExpiredException("Return period expired on " + OrderService.returnDeadline(o).toLocalDate() + " (" + AppConstants.RETURN_DAYS + "-day policy)");
        if (reason == null || !REASONS.contains(reason)) throw new InvalidRequestException("Please select a valid return reason");
        if (returns.existsByOrderItemAndStatusNot(item, ReturnStatus.REJECTED)) throw new ConflictException("A return has already been requested for this item");
        if (o.getPaymentMethod() == PaymentMethod.COD && banks.findFirstByUserAndPrimaryAccountTrue(u).isEmpty())
            throw new InvalidRequestException("Please add a bank account or UPI ID (Account > Bank Accounts) so we can send your COD refund");

        ReturnRequest r = new ReturnRequest();
        r.setOrderItem(item); r.setOrder(o); r.setUser(u); r.setReason(reason); r.setUserComment(comment);
        r.setImages(files.storeAll(photos, "returns", 5));
        returns.save(r);
        track(r, ReturnStatus.REQUESTED, "Return requested: " + reason);
        notif.notify(u, NotificationType.RETURN, "Your return request #" + r.getId() + " for order #" + o.getId() + " has been submitted.");
        notif.notify(item.getSeller(), NotificationType.RETURN, "Return requested for \"" + item.getProductName() + "\" (order #" + o.getId() + ").");
        notif.notifyAdmins(NotificationType.RETURN, "New return request #" + r.getId() + " (order #" + o.getId() + ").");
        return dto(r, true);
    }

    @Transactional(readOnly = true)
    public List<ReturnDto> mine(User u) { return returns.findByUserOrderByRequestedAtDesc(u).stream().map(r -> dto(r, false)).toList(); }

    @Transactional(readOnly = true)
    public ReturnDto get(User u, Long id, boolean withTracking) {
        ReturnRequest r = returns.findById(id).orElseThrow(() -> new NotFoundException("Return not found"));
        if (u.getRole() != Role.ADMIN && !r.getUser().getId().equals(u.getId())) throw new ForbiddenException("You cannot view this return");
        return dto(r, withTracking);
    }

    // ------------------------------------------------------------------ admin
    @Transactional(readOnly = true)
    public List<ReturnDto> all() { return returns.findAllByOrderByRequestedAtDesc().stream().map(r -> dto(r, true)).toList(); }

    @Transactional(readOnly = true)
    public List<ReturnDto> forSeller(User seller) { return returns.findBySeller(seller.getId()).stream().map(r -> dto(r, false)).toList(); }

    /** Admin moves a return through its stages. REFUND_INITIATED / REJECTED are the "product analysis" decisions. */
    public ReturnDto advance(Long id, ReturnStatus next, String note) {
        ReturnRequest r = returns.findById(id).orElseThrow(() -> new NotFoundException("Return not found"));
        ReturnStatus cur = r.getStatus();
        boolean ok = switch (cur) {
            case REQUESTED -> next == ReturnStatus.APPROVED || next == ReturnStatus.REJECTED;
            case APPROVED -> next == ReturnStatus.PICKUP_SCHEDULED;
            case PICKUP_SCHEDULED -> next == ReturnStatus.PICKED_UP;
            case PICKED_UP -> next == ReturnStatus.RECEIVED;
            case RECEIVED -> next == ReturnStatus.PRODUCT_ANALYSIS;
            case PRODUCT_ANALYSIS -> next == ReturnStatus.REFUND_INITIATED || next == ReturnStatus.REJECTED;
            default -> false;
        };
        if (!ok) throw new InvalidRequestException("Invalid return step: " + cur + " -> " + next);
        if (cur == ReturnStatus.PRODUCT_ANALYSIS && (note == null || note.isBlank()))
            throw new InvalidRequestException("Add your product analysis note (condition, accessories, packaging, damage)");

        r.setStatus(next);
        if (cur == ReturnStatus.PRODUCT_ANALYSIS) r.setAnalysisNote(note);
        User cust = r.getUser();
        switch (next) {
            case APPROVED -> { track(r, next, "Return approved"); notif.notifyAndEmail(cust, NotificationType.RETURN, "Return approved", "Your return request #" + r.getId() + " has been approved."); }
            case PICKUP_SCHEDULED -> { track(r, next, note == null || note.isBlank() ? "Pickup scheduled within 2 business days" : note); notif.notify(cust, NotificationType.RETURN, "Pickup scheduled for return #" + r.getId() + "."); }
            case PICKED_UP -> { track(r, next, "Delivery partner picked up the product"); notif.notify(cust, NotificationType.RETURN, "Your return #" + r.getId() + " was picked up."); }
            case RECEIVED -> { track(r, next, "Product received by the delivery team / seller warehouse"); notif.notify(cust, NotificationType.RETURN, "We received your returned product (return #" + r.getId() + ")."); }
            case PRODUCT_ANALYSIS -> { track(r, next, "Quality team is checking condition, accessories, packaging and warranty"); notif.notify(cust, NotificationType.RETURN, "Return #" + r.getId() + " is under product analysis."); }
            case REFUND_INITIATED -> { track(r, next, "Analysis passed. " + note); initiateRefund(r); }
            case REJECTED -> { track(r, next, cur == ReturnStatus.REQUESTED ? "Return rejected" + (note == null ? "" : ": " + note) : "Return rejected after analysis: " + note);
                               notif.notifyAndEmail(cust, NotificationType.RETURN, "Return rejected", "Your return request #" + r.getId() + " could not be approved." + (note == null ? "" : " Reason: " + note)); }
            default -> { }
        }
        return dto(r, true);
    }

    private void initiateRefund(ReturnRequest r) {
        OrderItem i = r.getOrderItem();
        Order o = r.getOrder();
        BigDecimal base = i.lineTotal();
        BigDecimal amount = base.add(base.multiply(AppConstants.TAX_RATE)).setScale(2, RoundingMode.HALF_UP);
        String dest;
        if (o.getPaymentMethod() == PaymentMethod.UPI) dest = "Original payment method (UPI)";
        else {
            BankAccount b = banks.findFirstByUserAndPrimaryAccountTrue(r.getUser()).orElseThrow(() -> new InvalidRequestException("Customer has no linked bank account"));
            dest = b.getBankName() + " " + MaskUtil.account(b.getAccountNumber()) + (b.getUpiId() != null && !b.getUpiId().isBlank() ? " / " + b.getUpiId() : "");
        }
        Refund f = new Refund();
        f.setReturnRequest(r); f.setOrder(o); f.setUser(r.getUser()); f.setAmount(amount); f.setDestination(dest);
        refunds.save(f);
        // The sale no longer counts for the seller: reverse the commission rows and put the item back in stock.
        for (SellerCommission c : commissions.findByOrderItem(i)) c.setPaymentStatus(CommissionStatus.REVERSED);
        products.lockById(i.getProduct().getId()).ifPresent(p -> p.setStock(p.getStock() + i.getQuantity()));
        notif.notifyAndEmail(r.getUser(), NotificationType.REFUND, "Refund initiated",
                "Your refund of Rs. " + amount + " for return #" + r.getId() + " has been initiated to " + dest + ".");
    }

    /** Simulated payout (no real banking call). A real gateway refund API would be invoked here. */
    public RefundDto completeRefund(Long refundId) {
        Refund f = refunds.findById(refundId).orElseThrow(() -> new NotFoundException("Refund not found"));
        if (f.getStatus() == RefundStatus.COMPLETED) throw new InvalidRequestException("Refund already completed");
        f.setStatus(RefundStatus.COMPLETED); f.setCompletedAt(LocalDateTime.now());
        ReturnRequest r = f.getReturnRequest();
        if (r != null) {
            r.setStatus(ReturnStatus.REFUNDED);
            track(r, ReturnStatus.REFUNDED, "Refund of Rs. " + f.getAmount() + " completed to " + f.getDestination());
            if (r.getOrder().getPaymentStatus() == PaymentStatus.SUCCESS && r.getOrder().getItems().size() == 1) r.getOrder().setPaymentStatus(PaymentStatus.REFUNDED);
        }
        notif.notifyAndEmail(f.getUser(), NotificationType.REFUND, "Refund completed", "Your refund of Rs. " + f.getAmount() + " has been completed to " + f.getDestination() + ".");
        return refundDto(f);
    }

    @Transactional(readOnly = true)
    public List<RefundDto> refundsFor(User u) { return refunds.findByUserOrderByCreatedAtDesc(u).stream().map(this::refundDto).toList(); }

    @Transactional(readOnly = true)
    public List<RefundDto> allRefunds() { return refunds.findAllByOrderByCreatedAtDesc().stream().map(this::refundDto).toList(); }

    private RefundDto refundDto(Refund f) {
        return new RefundDto(f.getId(), f.getReturnRequest() == null ? null : f.getReturnRequest().getId(), f.getOrder().getId(),
                f.getUser().getFullName(), f.getAmount(), f.getStatus(), f.getDestination(), f.getCreatedAt(), f.getCompletedAt());
    }

    private void track(ReturnRequest r, ReturnStatus s, String desc) {
        ReturnTracking t = new ReturnTracking();
        t.setReturnRequest(r); t.setStatus(s); t.setDescription(desc);
        trackingRepo.save(t);
    }

    private ReturnDto dto(ReturnRequest r, boolean withTracking) {
        OrderItem i = r.getOrderItem();
        List<ReturnTrackingDto> tr = withTracking
                ? trackingRepo.findByReturnRequestOrderByEventTimeAscIdAsc(r).stream().map(t -> new ReturnTrackingDto(t.getStatus(), t.getEventTime(), t.getDescription())).toList()
                : List.of();
        BigDecimal refundAmt = refunds.findByReturnRequest(r).map(Refund::getAmount).orElse(null);
        return new ReturnDto(r.getId(), r.getOrder().getId(), i.getId(), i.getProductName(), i.getImageUrl(), r.getUser().getFullName(),
                r.getReason(), r.getUserComment(), r.getStatus(), List.copyOf(r.getImages()), r.getRequestedAt(), r.getAnalysisNote(), refundAmt, tr);
    }
}
