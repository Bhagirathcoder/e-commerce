package com.bytebazaar.service;

import com.bytebazaar.dto.Dtos.*;
import com.bytebazaar.entity.*;
import com.bytebazaar.exception.*;
import com.bytebazaar.repository.*;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UPI payment flow. The browser can NEVER mark a payment as successful: /verify asks the PaymentGateway adapter
 * (server-side) for the real status. Replace SimulatedPaymentGateway with a Razorpay/Cashfree adapter for production.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {
    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final NotificationService notif;
    private final PaymentGateway gateway;
    @Value("${bytebazaar.payment.merchant-upi}") private String merchantUpi;
    @Value("${bytebazaar.payment.simulate:true}") private boolean simulate;

    private Payment ownedPayment(User u, Long paymentId) {
        Payment p = payments.findById(paymentId).orElseThrow(() -> new PaymentException("Payment not found"));
        if (!p.getOrder().getUser().getId().equals(u.getId())) throw new ForbiddenException("You cannot access this payment");
        return p;
    }

    private PaymentDto dto(Payment p) {
        String uri = "upi://pay?pa=" + merchantUpi + "&pn=ByteBazaar&am=" + p.getAmount().toPlainString()
                + "&cu=INR&tn=Order%20" + p.getOrder().getId() + "&tr=" + p.getGatewayRef();
        return new PaymentDto(p.getId(), p.getOrder().getId(), p.getMethod(), p.getAmount(), p.getStatus(), uri, merchantUpi, simulate);
    }

    /** Returns the payment record created with the order (UPI) so the payment page can show amount / UPI id / QR. */
    public PaymentDto create(User u, Long orderId) {
        Order o = orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException("Order not found"));
        if (!o.getUser().getId().equals(u.getId())) throw new ForbiddenException("You cannot access this order");
        if (o.getPaymentMethod() != PaymentMethod.UPI) throw new PaymentException("This order is Cash on Delivery");
        if (o.getStatus() == OrderStatus.CANCELLED) throw new PaymentException("This order was cancelled");
        Payment p = payments.findFirstByOrderOrderByIdDesc(o).orElseThrow(() -> new PaymentException("Payment record missing"));
        return dto(p);
    }

    /** DEMO ONLY: stands in for the customer approving the request in their UPI app. Disabled when simulate=false. */
    public PaymentDto simulatePay(User u, Long paymentId) {
        if (!simulate) throw new ForbiddenException("Payment simulator is disabled");
        Payment p = ownedPayment(u, paymentId);
        if (p.getStatus() == PaymentStatus.PENDING) p.setGatewayStatus("PAID");
        return dto(p);
    }

    public PaymentDto verify(User u, Long paymentId) {
        Payment p = ownedPayment(u, paymentId);
        if (p.getStatus() == PaymentStatus.SUCCESS) return dto(p);
        Order o = p.getOrder();
        if (o.getStatus() == OrderStatus.CANCELLED) throw new PaymentException("This order was cancelled");
        String status = gateway.fetchStatus(p);
        if ("PAID".equals(status)) {
            p.setStatus(PaymentStatus.SUCCESS); p.setVerifiedAt(LocalDateTime.now());
            o.setPaymentStatus(PaymentStatus.SUCCESS);
            o.setStatus(OrderStatus.CONFIRMED);
            orderService.addTracking(o, OrderStatus.CONFIRMED, "ByteBazaar", "Payment received - order confirmed");
            notif.notifyAndEmail(u, NotificationType.PAYMENT, "Payment received", "We received Rs. " + p.getAmount() + " for order #" + o.getId() + ". Your order is confirmed.");
            orderService.notifySellersAndAdmin(o, orderService.sellersOf(o));
            return dto(p);
        }
        if ("FAILED".equals(status)) {
            p.setStatus(PaymentStatus.FAILED);
            throw new PaymentException("Payment failed. Please try again.");
        }
        throw new PaymentException("Payment not received yet. If you already paid, wait a few seconds and press Verify again.");
    }
}
