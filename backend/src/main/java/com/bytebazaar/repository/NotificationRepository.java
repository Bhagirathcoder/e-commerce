package com.bytebazaar.repository;

import com.bytebazaar.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable p);
    long countByUserAndSeenFalse(User user);
    Optional<Notification> findByIdAndUser(Long id, User user);
    @Modifying @Query("update Notification n set n.seen = true where n.user = :u")
    int markAllSeen(@Param("u") User user);
}
