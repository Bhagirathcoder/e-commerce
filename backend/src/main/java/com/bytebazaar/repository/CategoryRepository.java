package com.bytebazaar.repository;

import com.bytebazaar.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByParentIsNullOrderByIdAsc();
    List<Category> findByParentOrderByIdAsc(Category parent);
    Optional<Category> findFirstByNameIgnoreCase(String name);
}
