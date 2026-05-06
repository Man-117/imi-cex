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
public class OrderResponse {
    private Long id;
    private Long userId;
    private String orderType;
    private String baseCurrency;
    private String quoteCurrency;
    private BigDecimal amount;
    private BigDecimal price;
    private BigDecimal filledAmount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

