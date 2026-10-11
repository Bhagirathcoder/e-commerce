package com.bytebazaar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderScheduler {
    private final OrderService orders;

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void cancelUnpaid() {
        int n = orders.cancelUnpaidExpired();
        if (n > 0) log.info("Auto-cancelled {} unpaid UPI order(s) and released their stock", n);
    }
}
