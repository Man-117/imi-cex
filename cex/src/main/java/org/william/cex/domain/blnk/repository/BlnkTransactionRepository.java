package org.william.cex.domain.blnk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.william.cex.domain.blnk.entity.BlnkTransaction;

import java.util.List;

@Repository
public interface BlnkTransactionRepository extends JpaRepository<BlnkTransaction, Long> {
    List<BlnkTransaction> findByStatus(BlnkTransaction.Status status);
    List<BlnkTransaction> findByLocalOrderId(Long orderId);
}

