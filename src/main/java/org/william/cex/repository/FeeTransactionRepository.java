package org.william.cex.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.william.cex.entity.FeeTransaction;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Repository
public interface FeeTransactionRepository extends JpaRepository<FeeTransaction, Long> {
    List<FeeTransaction> findByOrderId(Long orderId);

    @Query("""
            SELECT f.orderId, COALESCE(SUM(f.amount), 0)
            FROM FeeTransaction f
            WHERE f.orderId IN :orderIds
              AND f.feeType = :feeType
            GROUP BY f.orderId
            """)
    List<Object[]> sumFeesByOrderIdsAndType(@Param("orderIds") Collection<Long> orderIds,
                                            @Param("feeType") FeeTransaction.FeeType feeType);

    @Query("SELECT SUM(f.amount) FROM FeeTransaction f")
    BigDecimal getTotalFees();
}

