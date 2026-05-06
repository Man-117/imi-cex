package org.william.cex.domain.fee.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.william.cex.domain.fee.entity.FeeTransaction;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface FeeTransactionRepository extends JpaRepository<FeeTransaction, Long> {
    List<FeeTransaction> findByOrderId(Long orderId);

    @Query("SELECT SUM(f.amount) FROM FeeTransaction f")
    BigDecimal getTotalFees();
}

