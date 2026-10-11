package com.bytebazaar.service;

import com.bytebazaar.entity.Payment;
import org.springframework.stereotype.Component;

/** Demo gateway: status is whatever the (server-side) simulator recorded. No real money moves. */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {
    @Override
    public String fetchStatus(Payment p) {
        String s = p.getGatewayStatus();
        return "PAID".equals(s) || "FAILED".equals(s) ? s : "PENDING";
    }
}
