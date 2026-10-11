package com.bytebazaar.repository;

import com.bytebazaar.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserOrderByCreatedAtDesc(User user);
    List<Order> findAllByOrderByCreatedAtDesc();
    long countByUser(User user);
    long countByStatus(OrderStatus s);

    @Query("select distinct o from Order o join o.items i where i.seller.id = :sid order by o.createdAt desc")
    List<Order> findBySeller(@Param("sid") Long sellerId);

    @Query("select count(distinct o) from Order o join o.items i where i.seller.id = :sid")
    long countBySeller(@Param("sid") Long sellerId);

    @Query("select count(distinct o) from Order o join o.items i where i.seller.id = :sid and o.status = :st")
    long countBySellerAndStatus(@Param("sid") Long sellerId, @Param("st") OrderStatus st);

    List<Order> findByPaymentMethodAndPaymentStatusAndStatusAndCreatedAtBefore(
            PaymentMethod m, PaymentStatus ps, OrderStatus s, LocalDateTime before);

    @Query("select coalesce(sum(o.total),0) from Order o where o.status <> com.bytebazaar.entity.OrderStatus.CANCELLED")
    BigDecimal totalSales();
}
