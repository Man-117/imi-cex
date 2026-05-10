package org.william.cex.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeReconciliationIssueResponse {
    private Long id;
    private String issueType;
    private String severity;
    private String referenceType;
    private Long referenceId;
    private String message;
    private BigDecimal expectedValue;
    private BigDecimal actualValue;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
