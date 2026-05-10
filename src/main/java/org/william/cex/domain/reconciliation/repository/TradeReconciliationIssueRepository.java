package org.william.cex.domain.reconciliation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.william.cex.domain.reconciliation.entity.TradeReconciliationIssue;

import java.util.List;

@Repository
public interface TradeReconciliationIssueRepository extends JpaRepository<TradeReconciliationIssue, Long> {
    List<TradeReconciliationIssue> findByRunIdOrderByIdAsc(Long runId);

    List<TradeReconciliationIssue> findByRunIdAndStatus(Long runId, TradeReconciliationIssue.IssueStatus status);
}
