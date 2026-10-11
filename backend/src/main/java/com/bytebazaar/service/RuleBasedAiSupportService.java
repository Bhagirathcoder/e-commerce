package com.bytebazaar.service;

import com.bytebazaar.dto.Dtos.*;
import com.bytebazaar.entity.*;
import com.bytebazaar.repository.*;
import com.bytebazaar.util.AppConstants;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleBasedAiSupportService implements AiSupportService {
    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final List<String> QUICK = List.of("Where is my order?", "How do I return a product?", "Refund status", "Payment help", "Warranty help", "Chat with admin");
    private final OrderRepository orders;
    private final OrderTrackingRepository tracking;
    private final ReturnRequestRepository returns;
    private final RefundRepository refunds;

    @Override
    public AiReply answer(User u, String message, Long orderId) {
        String m = message.toLowerCase();
        if (orderId == null) {
            Matcher x = Pattern.compile("#?(\\d{1,9})").matcher(m);
            if (x.find()) orderId = Long.valueOf(x.group(1));
        }
        if (m.matches("^(hi|hello|hey|hii|namaste)\\b.*")) return new AiReply("Hello " + u.getFullName().split(" ")[0] + "! Welcome to ByteBazaar. How can I help you today?", QUICK);

        if (has(m, "where", "track", "status", "delivery", "shipped", "arrive", "order")
                && !has(m, "return", "refund", "cancel", "warranty")) return orderStatus(u, orderId);
        if (has(m, "cancel")) return cancel(u, orderId);
        if (has(m, "refund", "money back")) return refund(u);
        if (has(m, "return", "replace", "exchange")) return returnHelp(u);
        if (has(m, "warranty", "guarantee")) return new AiReply("Products with a warranty get a Warranty Certificate PDF once the order is delivered. Open Orders > Order Details > \"Download Warranty\". The warranty starts on the delivery date and the period is shown on the product page.", List.of("Where is my order?", "Chat with admin"));
        if (has(m, "payment", "upi", "cod", "cash", "paid", "pay")) return new AiReply("We accept Cash on Delivery and UPI. UPI orders are confirmed only after our server verifies the payment; unpaid UPI orders are auto-cancelled after " + AppConstants.UNPAID_UPI_MINUTES + " minutes and the stock is released. If money was debited but the order shows pending, press \"Verify payment\" on the payment page or chat with admin.", List.of("Refund status", "Chat with admin"));
        if (has(m, "account", "password", "profile", "email", "phone", "address", "bank")) return new AiReply("You can edit your profile, address, phone, email and bank accounts under Account. Use \"Forgot password?\" on the login page to reset your password by email.", List.of("Chat with admin"));
        if (has(m, "invoice", "bill")) return new AiReply("Invoices are available from Orders > Order Details > \"Download Invoice\" as soon as the order is placed.", List.of("Where is my order?"));
        if (has(m, "product", "stock", "price", "available")) return new AiReply("Use the search bar for products, brands and categories. Each product page shows live stock, delivery info, warranty and return policy. Only verified products are listed.", List.of("Where is my order?", "Chat with admin"));
        if (has(m, "support", "human", "agent", "admin", "talk")) return new AiReply("Open \"Chat with Admin\" on the Help & Support page and a ByteBazaar admin will reply to you there.", List.of("Where is my order?"));
        return new AiReply("I can help with orders, payments, shipping, delivery, returns, refunds, warranty, account and cancellation. I don't have an answer for that one - please use \"Chat with Admin\" and a person will help you.", QUICK);
    }

    private boolean has(String m, String... keys) { for (String k : keys) if (m.contains(k)) return true; return false; }

    private AiReply orderStatus(User u, Long orderId) {
        if (orderId != null) {
            Optional<Order> o = orders.findById(orderId).filter(x -> x.getUser().getId().equals(u.getId()));
            if (o.isEmpty()) return new AiReply("I couldn't find order #" + orderId + " in your account. Please check the order ID, or pick one of your recent orders below.", recentOrderChips(u));
            return new AiReply(describe(o.get()), List.of("Return help", "Refund status", "Chat with admin"));
        }
        List<Order> list = orders.findByUserOrderByCreatedAtDesc(u);
        if (list.isEmpty()) return new AiReply("You haven't placed any orders yet. Browse products and place your first order!", List.of("Payment help"));
        if (list.size() == 1) return new AiReply(describe(list.get(0)), List.of("Return help", "Refund status", "Chat with admin"));
        return new AiReply("Please select or enter your order ID. Your latest orders: " + summary(list), recentOrderChips(u));
    }

    private String summary(List<Order> list) {
        StringBuilder sb = new StringBuilder();
        list.stream().limit(3).forEach(o -> sb.append("#").append(o.getId()).append(" (").append(o.getStatus()).append(") "));
        return sb.toString().trim();
    }

    private List<String> recentOrderChips(User u) {
        List<String> chips = new ArrayList<>();
        orders.findByUserOrderByCreatedAtDesc(u).stream().limit(3).forEach(o -> chips.add("Track order #" + o.getId()));
        return chips;
    }

    private String describe(Order o) {
        List<OrderTracking> t = tracking.findByOrderOrderByEventTimeAscIdAsc(o);
        StringBuilder sb = new StringBuilder("Order #" + o.getId() + " is currently " + o.getStatus().name().replace('_', ' ') + ". ");
        if (!t.isEmpty()) {
            OrderTracking last = t.get(t.size() - 1);
            sb.append("Latest update: ").append(last.getDescription()).append(" (").append(last.getEventTime().format(F)).append(", ").append(last.getLocation()).append("). ");
        }
        if (o.getPaymentMethod() == PaymentMethod.UPI && o.getPaymentStatus() == PaymentStatus.PENDING && o.getStatus() == OrderStatus.PLACED)
            sb.append("Your UPI payment has not been verified yet - complete it from the payment page to confirm the order. ");
        if (o.getStatus() == OrderStatus.DELIVERED && o.getDeliveredAt() != null)
            sb.append("Delivered on ").append(o.getDeliveredAt().format(F)).append("; the return window ends ").append(OrderService.returnDeadline(o).format(F)).append(". ");
        else if (o.getStatus() != OrderStatus.CANCELLED && o.getEstimatedDelivery() != null)
            sb.append("Estimated delivery: ").append(o.getEstimatedDelivery()).append(". ");
        sb.append("Payment: ").append(o.getPaymentMethod()).append(" / ").append(o.getPaymentStatus()).append(". Total Rs. ").append(o.getTotal()).append(".");
        return sb.toString();
    }

    private AiReply cancel(User u, Long orderId) {
        String base = "You can cancel an order yourself while it is Placed or Confirmed (Orders > Order Details > Cancel Order). After it ships, wait for delivery and use the return option within " + AppConstants.RETURN_DAYS + " days.";
        if (orderId != null) {
            Optional<Order> o = orders.findById(orderId).filter(x -> x.getUser().getId().equals(u.getId()));
            if (o.isPresent()) {
                boolean can = o.get().getStatus() == OrderStatus.PLACED || o.get().getStatus() == OrderStatus.CONFIRMED;
                return new AiReply("Order #" + orderId + " is " + o.get().getStatus() + ". " + (can ? "It can still be cancelled from Order Details." : "It can no longer be cancelled."), List.of("Return help", "Chat with admin"));
            }
        }
        return new AiReply(base, recentOrderChips(u));
    }

    private AiReply refund(User u) {
        List<Refund> list = refunds.findByUserOrderByCreatedAtDesc(u);
        if (list.isEmpty()) return new AiReply("You have no refunds right now. Refunds start after a return passes product analysis (or a paid order is cancelled) and go to your linked bank account / original payment method.", List.of("How do I return a product?"));
        Refund f = list.get(0);
        return new AiReply("Your latest refund: Rs. " + f.getAmount() + " for order #" + f.getOrder().getId() + " is " + f.getStatus() + " (to " + f.getDestination() + ", started " + f.getCreatedAt().format(F) + ").", List.of("Chat with admin"));
    }

    private AiReply returnHelp(User u) {
        StringBuilder sb = new StringBuilder("Eligible items can be returned within " + AppConstants.RETURN_DAYS + " days of delivery (Orders > Order Details > Return Product). Flow: request > approval > pickup > received > product analysis > refund. ");
        List<ReturnRequest> mine = returns.findByUserOrderByRequestedAtDesc(u);
        if (!mine.isEmpty()) sb.append("Your latest return #").append(mine.get(0).getId()).append(" for order #").append(mine.get(0).getOrder().getId()).append(" is ").append(mine.get(0).getStatus()).append(".");
        return new AiReply(sb.toString(), List.of("Refund status", "Chat with admin"));
    }
}
