package org.william.cex.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.william.cex.api.dto.request.UpdateFeeRateRequest;
import org.william.cex.api.dto.response.FeeRateResponse;
import org.william.cex.domain.admin.service.AdminService;
import org.william.cex.domain.fee.entity.FeeRate;
import org.william.cex.domain.fee.service.FeeService;
import org.william.cex.infrastructure.security.AuthenticationUtils;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/fees")
@Slf4j
public class FeeController {

    @Autowired
    private FeeService feeService;

    @Autowired
    AdminService adminService;

    @Autowired
    AuthenticationUtils authenticationUtils;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FeeRateResponse> updateFeeRate(
            @Valid @RequestBody UpdateFeeRateRequest request,
            HttpServletRequest httpServletRequest) {

        try {
            Long adminId = authenticationUtils.getAuthenticatedUserId();

            log.info("Admin {} is updating fee rate for pair: {}", adminId, request.getCurrencyPair());

            FeeRate feeRate = feeService.updateFeeRate(
                    request.getCurrencyPair(),
                    request.getFeePercentage(),
                    adminId
            );

            FeeRateResponse response = FeeRateResponse.builder()
                    .id(feeRate.getId())
                    .currencyPair(feeRate.getCurrencyPair())
                    .feePercentage(feeRate.getFeePercentage())
                    .build();

            HashMap<String, Object> auditChange = new HashMap<>();
            auditChange.put("currencyPair", request.getCurrencyPair());
            auditChange.put("feePercentage", request.getFeePercentage());
            adminService.recordAuditAction(
                    adminId,
                    "UPDATE_FEE_RATE",
                    "fee_rates",
                    auditChange,
                    httpServletRequest.getRemoteAddr()
            );

            log.info("Fee rate updated successfully for pair: {} by admin: {}", request.getCurrencyPair(), adminId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error updating fee rate", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/{pair}")
    public ResponseEntity<FeeRateResponse> getFeeRate(@PathVariable String pair) {
        try {
            log.info("Requested fee rate for currency pair: {}", pair);

            FeeRate feeRate = feeService.getFeeRate(pair);
            FeeRateResponse response = FeeRateResponse.builder()
                    .id(feeRate.getId())
                    .currencyPair(feeRate.getCurrencyPair())
                    .feePercentage(feeRate.getFeePercentage())
                    .build();

            log.info("Fee rate retrieved for {}: {}%", pair, feeRate.getFeePercentage());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting fee rate for pair: {}", pair, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<FeeRateResponse>> getAllFeeRates() {
        try {
            log.info("Requested all fee rates");

            List<FeeRate> feeRates = feeService.getAllFeeRates();
            List<FeeRateResponse> responses = feeRates.stream()
                    .map(rate -> FeeRateResponse.builder()
                            .id(rate.getId())
                            .currencyPair(rate.getCurrencyPair())
                            .feePercentage(rate.getFeePercentage())
                            .build())
                    .collect(Collectors.toList());

            log.info("All fee rates retrieved: {} currency pairs", responses.size());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error getting fee rates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

