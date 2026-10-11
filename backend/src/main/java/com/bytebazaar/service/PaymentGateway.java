package com.bytebazaar.service;

import com.bytebazaar.entity.Payment;

/** Adapter for a real payment gateway (Razorpay, Cashfree...). Must return PAID / FAILED / PENDING based on the gateway's server API. */
public interface PaymentGateway {
    String fetchStatus(Payment payment);
}
