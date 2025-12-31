package org.william.cex.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BalanceResponse {
    private Long userId;
    private String currency;
    private BigDecimal balance;
    private BigDecimal lockedAmount;
    private BigDecimal availableBalance;
}

