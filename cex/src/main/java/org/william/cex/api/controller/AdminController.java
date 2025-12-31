package org.william.cex.api.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.william.cex.api.dto.request.UpdateFeeRateRequest;
import org.william.cex.api.dto.response.AccountBalanceResponse;
import org.william.cex.api.dto.response.FeeRateResponse;
import org.william.cex.domain.fee.entity.FeeRate;
import org.william.cex.domain.fee.service.FeeService;
import org.william.cex.domain.user.entity.UserAccount;
import org.william.cex.domain.user.repository.UserAccountRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/admin")
@Slf4j
public class AdminController {

    @Autowired
    private FeeService feeService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @GetMapping("/account/balance")
    public ResponseEntity<AccountBalanceResponse> getAccountBalance() {
        try {
            // Calculate firm's total revenue
            List<UserAccount> allAccounts = userAccountRepository.findAll();
            BigDecimal totalDeposits = allAccounts.stream()
                    .map(UserAccount::getTotalDeposits)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalWithdrawals = allAccounts.stream()
                    .map(UserAccount::getTotalWithdrawals)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalFees = feeService.getTotalFees();

            // Firm holdings = deposits - withdrawals + fees collected
            BigDecimal firmHoldings = totalDeposits.subtract(totalWithdrawals).add(totalFees);

            AccountBalanceResponse response = AccountBalanceResponse.builder()
                    .totalDeposits(totalDeposits)
                    .totalWithdrawals(totalWithdrawals)
                    .totalFees(totalFees)
                    .firmHoldings(firmHoldings)
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting account balance", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/fees")
    public ResponseEntity<FeeRateResponse> updateFeeRate(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody UpdateFeeRateRequest request) {

        try {
            Long adminId = extractUserIdFromAuth(authHeader);

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

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error updating fee rate", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/fees")
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

    private Long extractUserIdFromAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Invalid or missing Authorization header");
        }
        try {
            return Long.parseLong(authHeader.substring(7));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid user ID in Authorization header");
        }
    }
}

