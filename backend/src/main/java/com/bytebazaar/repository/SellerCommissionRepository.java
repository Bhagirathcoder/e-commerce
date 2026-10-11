package com.bytebazaar.repository;

import com.bytebazaar.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SellerCommissionRepository extends JpaRepository<SellerCommission, Long> {
    List<SellerCommission> findBySellerOrderByCreatedAtDesc(User seller);
    List<SellerCommission> findAllByOrderByCreatedAtDesc();
    List<SellerCommission> findByOrderItem(OrderItem item);

    @Query("select coalesce(sum(c.saleAmount),0) from SellerCommission c where c.paymentStatus <> com.bytebazaar.entity.CommissionStatus.REVERSED and (:sid is null or c.seller.id = :sid)")
    BigDecimal sumSales(@Param("sid") Long sellerId);
    @Query("select coalesce(sum(c.commissionAmount),0) from SellerCommission c where c.paymentStatus <> com.bytebazaar.entity.CommissionStatus.REVERSED and (:sid is null or c.seller.id = :sid)")
    BigDecimal sumCommission(@Param("sid") Long sellerId);
    @Query("select coalesce(sum(c.sellerAmount),0) from SellerCommission c where c.paymentStatus <> com.bytebazaar.entity.CommissionStatus.REVERSED and (:sid is null or c.seller.id = :sid)")
    BigDecimal sumSellerAmount(@Param("sid") Long sellerId);
    @Query("select coalesce(sum(c.commissionAmount),0) from SellerCommission c where c.paymentStatus = com.bytebazaar.entity.CommissionStatus.PENDING and (:sid is null or c.seller.id = :sid)")
    BigDecimal sumPendingCommission(@Param("sid") Long sellerId);
    @Query("select coalesce(sum(c.sellerAmount),0) from SellerCommission c where c.paymentStatus = com.bytebazaar.entity.CommissionStatus.PAID and (:sid is null or c.seller.id = :sid)")
    BigDecimal sumPaidOut(@Param("sid") Long sellerId);
}
