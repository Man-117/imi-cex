package org.william.cex.domain.reconciliation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.william.cex.domain.reconciliation.entity.TradeReconciliationRun;

import java.util.List;

@Repository
public interface TradeReconciliationRunRepository extends JpaRepository<TradeReconciliationRun, Long> {
    List<TradeReconciliationRun> findTop20ByOrderByStartedAtDesc();
}
