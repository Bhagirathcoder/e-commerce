package com.bytebazaar.repository;

import com.bytebazaar.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {
    List<ReturnRequest> findByUserOrderByRequestedAtDesc(User user);
    List<ReturnRequest> findAllByOrderByRequestedAtDesc();
    List<ReturnRequest> findByOrderItemOrderByIdDesc(OrderItem item);
    boolean existsByOrderItemAndStatusNot(OrderItem item, ReturnStatus status);
    long countByStatus(ReturnStatus s);
    @Query("select r from ReturnRequest r where r.orderItem.seller.id = :sid order by r.requestedAt desc")
    List<ReturnRequest> findBySeller(@Param("sid") Long sellerId);
}
