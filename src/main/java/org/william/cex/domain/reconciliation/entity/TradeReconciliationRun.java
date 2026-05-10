package org.william.cex.domain.reconciliation.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "trade_reconciliation_runs", indexes = {
        @Index(name = "idx_trade_recon_runs_started_at", columnList = "started_at DESC"),
        @Index(name = "idx_trade_recon_runs_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReconciliationType reconciliationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status;

    @Column(nullable = false)
    private LocalDateTime scopeFrom;

    @Column(nullable = false)
    private LocalDateTime scopeTo;

    @Column(nullable = false)
    private Integer totalTradesScanned;

    @Column(nullable = false)
    private Integer totalIssues;

    @Column(nullable = false)
    private Integer autoRepairedIssues;

    @Column(length = 500)
    private String failureReason;

    @Column(columnDefinition = "jsonb")
    private JsonNode summary;

    @Column(nullable = false, updatable = false)
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
        if (totalTradesScanned == null) {
            totalTradesScanned = 0;
        }
        if (totalIssues == null) {
            totalIssues = 0;
        }
        if (autoRepairedIssues == null) {
            autoRepairedIssues = 0;
        }
    }

    public enum ReconciliationType {
        TRADE_SETTLEMENT
    }

    public enum RunStatus {
        RUNNING,
        COMPLETED,
        COMPLETED_WITH_BREAKS,
        FAILED,
        REPAIRED
    }
}
