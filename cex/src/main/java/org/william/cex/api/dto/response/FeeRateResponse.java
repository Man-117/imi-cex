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
public class FeeRateResponse {
    private Long id;
    private String currencyPair;
    private BigDecimal feePercentage;
}

