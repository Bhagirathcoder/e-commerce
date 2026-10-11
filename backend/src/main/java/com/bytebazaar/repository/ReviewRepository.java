package com.bytebazaar.repository;

import com.bytebazaar.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProductOrderByCreatedAtDesc(Product p);
    List<Review> findTop8ByRatingGreaterThanEqualOrderByCreatedAtDesc(int rating);
    boolean existsByUserAndProduct(User u, Product p);
    @Query("select coalesce(avg(r.rating),0) from Review r where r.product = :p")
    double avgFor(@Param("p") Product p);
    long countByProduct(Product p);
}
