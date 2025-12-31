package org.william.cex.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateFeeRateRequest {

    @NotBlank(message = "Currency pair is required")
    private String currencyPair;

    @DecimalMin(value = "0.0", message = "Fee percentage must be at least 0")
    private BigDecimal feePercentage;
}

