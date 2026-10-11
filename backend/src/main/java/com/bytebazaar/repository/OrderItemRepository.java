package com.bytebazaar.repository;

import com.bytebazaar.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    @Query("select count(i) > 0 from OrderItem i where i.order.user.id = :uid and i.product.id = :pid and i.order.status = :st")
    boolean existsPurchase(@Param("uid") Long userId, @Param("pid") Long productId, @Param("st") OrderStatus st);

    long countByProduct(Product product);

    @Query("select coalesce(sum(i.unitPrice * i.quantity),0) from OrderItem i where i.seller.id = :sid and i.order.status <> com.bytebazaar.entity.OrderStatus.CANCELLED")
    BigDecimal salesBySeller(@Param("sid") Long sellerId);

    @Query("select i from OrderItem i where i.order.createdAt >= :from and i.order.status <> com.bytebazaar.entity.OrderStatus.CANCELLED and (:sid is null or i.seller.id = :sid)")
    List<OrderItem> findForAnalytics(@Param("from") LocalDateTime from, @Param("sid") Long sellerId);
}
