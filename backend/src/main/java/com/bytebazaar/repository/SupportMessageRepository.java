package com.bytebazaar.repository;

import com.bytebazaar.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    List<SupportMessage> findByUserOrderByCreatedAtAscIdAsc(User user);
    long countByUserAndSenderAndSeenFalse(User user, SupportSender sender);
    long countBySenderAndSeenFalse(SupportSender sender);
    @Query("select m.user.id from SupportMessage m group by m.user.id order by max(m.createdAt) desc")
    List<Long> conversationUserIds();
    Optional<SupportMessage> findFirstByUserOrderByIdDesc(User user);
}
