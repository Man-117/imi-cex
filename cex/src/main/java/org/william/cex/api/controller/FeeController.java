package org.william.cex.api.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.william.cex.api.dto.response.FeeRateResponse;
import org.william.cex.domain.fee.entity.FeeRate;
import org.william.cex.domain.fee.service.FeeService;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/fees")
@Slf4j
public class FeeController {

    @Autowired
    private FeeService feeService;

    @GetMapping("/{pair}")
    public ResponseEntity<FeeRateResponse> getFeeRate(@PathVariable String pair) {
        try {
            FeeRate feeRate = feeService.getFeeRate(pair);
            FeeRateResponse response = FeeRateResponse.builder()
                    .id(feeRate.getId())
                    .currencyPair(feeRate.getCurrencyPair())
                    .feePercentage(feeRate.getFeePercentage())
                    .build();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting fee rate", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<FeeRateResponse>> getAllFeeRates() {
        try {
            List<FeeRate> feeRates = feeService.getAllFeeRates();
            List<FeeRateResponse> responses = feeRates.stream()
                    .map(rate -> FeeRateResponse.builder()
                            .id(rate.getId())
                            .currencyPair(rate.getCurrencyPair())
                            .feePercentage(rate.getFeePercentage())
                            .build())
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error getting fee rates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

