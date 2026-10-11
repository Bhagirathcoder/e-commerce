package com.bytebazaar.repository;

import com.bytebazaar.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    List<BankAccount> findByUserOrderByIdAsc(User user);
    Optional<BankAccount> findByIdAndUser(Long id, User user);
    Optional<BankAccount> findFirstByUserAndPrimaryAccountTrue(User user);
    boolean existsByUser(User user);
}
