package org.william.cex.domain.reconciliation.entity;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trade_reconciliation_issues", indexes = {
        @Index(name = "idx_trade_recon_issues_run_id", columnList = "run_id"),
        @Index(name = "idx_trade_recon_issues_status", columnList = "status"),
        @Index(name = "idx_trade_recon_issues_type", columnList = "issue_type")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeReconciliationIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueType issueType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReferenceType referenceType;

    @Column(nullable = false)
    private Long referenceId;

    @Column(nullable = false, length = 500)
    private String message;

    private BigDecimal expectedValue;

    private BigDecimal actualValue;

    @Column(columnDefinition = "JSONB")
    @Type(JsonBinaryType.class)
    private JsonNode metadata;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = IssueStatus.OPEN;
        }
    }

    public enum IssueType {
        UNSETTLED_TRADE,
        MISSING_ORDER,
        ORDER_SIDE_MISMATCH,
        ORDER_PAIR_MISMATCH,
        ORDER_FILLED_MISMATCH,
        FEE_LEDGER_MISMATCH
    }

    public enum Severity {
        HIGH,
        MEDIUM,
        LOW
    }

    public enum ReferenceType {
        TRADE,
        ORDER
    }

    public enum IssueStatus {
        OPEN,
        AUTO_REPAIRED,
        MANUAL_REVIEW
    }
}
