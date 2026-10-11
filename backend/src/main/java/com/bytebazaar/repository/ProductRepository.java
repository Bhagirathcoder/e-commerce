package com.bytebazaar.repository;

import com.bytebazaar.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> lockById(@Param("id") Long id);

    List<Product> findBySellerAndDeletedFalseOrderByIdDesc(User seller);
    List<Product> findByDeletedFalseOrderByIdDesc();
    List<Product> findByVerificationAndActiveTrueAndDeletedFalse(VerificationStatus v);
    long countBySellerAndDeletedFalse(User seller);
    long countByVerificationAndDeletedFalse(VerificationStatus v);
    long countByDeletedFalse();
    boolean existsBySku(String sku);
    long countByCategory(Category c);

    @Query("select distinct p.brand from Product p where p.verification = com.bytebazaar.entity.VerificationStatus.VERIFIED and p.active = true and p.deleted = false and p.brand is not null order by p.brand")
    List<String> brands();
}
