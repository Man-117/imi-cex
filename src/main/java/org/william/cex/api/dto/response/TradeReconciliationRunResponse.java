package org.william.cex.api.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeReconciliationRunResponse {
    private Long id;
    private String reconciliationType;
    private String status;
    private LocalDateTime scopeFrom;
    private LocalDateTime scopeTo;
    private Integer totalTradesScanned;
    private Integer totalIssues;
    private Integer autoRepairedIssues;
    private String failureReason;
    private JsonNode summary;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private List<TradeReconciliationIssueResponse> issues;
}
