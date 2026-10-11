package com.bytebazaar.repository;

import com.bytebazaar.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ReturnTrackingRepository extends JpaRepository<ReturnTracking, Long> {
    List<ReturnTracking> findByReturnRequestOrderByEventTimeAscIdAsc(ReturnRequest r);
}
