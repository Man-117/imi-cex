package org.william.cex.api.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.william.cex.api.dto.request.AdminRegisterRequest;
import org.william.cex.api.dto.request.LoginRequest;
import org.william.cex.api.dto.request.UpdateFeeRateRequest;
import org.william.cex.api.dto.response.AccountBalanceResponse;
import org.william.cex.api.dto.response.AuthResponse;
import org.william.cex.api.dto.response.FeeRateResponse;
import org.william.cex.api.dto.response.TradeReconciliationRunResponse;
import org.william.cex.api.exception.ReconciliationNotFoundException;
import org.william.cex.domain.admin.service.AdminService;
import org.william.cex.domain.fee.entity.FeeRate;
import org.william.cex.domain.fee.service.FeeService;
import org.william.cex.domain.reconciliation.service.TradeReconciliationService;
import org.william.cex.domain.user.entity.User;
import org.william.cex.domain.user.entity.UserAccount;
import org.william.cex.domain.user.repository.UserAccountRepository;
import org.william.cex.domain.user.service.UserService;
import org.william.cex.infrastructure.security.AuthenticationUtils;
import org.william.cex.infrastructure.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
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

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationUtils authenticationUtils;

    @Autowired
    private AdminService adminService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TradeReconciliationService tradeReconciliationService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> registerAdmin(@Valid @RequestBody AdminRegisterRequest request) {
        try {
            User admin = adminService.registerAdmin(request.getEmail(), request.getPassword(), request.getAdminKey());
            String token = jwtTokenProvider.generateToken(admin.getId(), admin.getEmail(), admin.getRole().toString());

            AuthResponse response = AuthResponse.builder()
                    .token(token)
                    .userId(admin.getId())
                    .email(admin.getEmail())
                    .role(admin.getRole().toString())
                    .build();

            log.info("Admin registered successfully: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Admin registration failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error during admin registration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> loginAdmin(@Valid @RequestBody LoginRequest request) {
        try {
            User admin = adminService.loginAdmin(request.getEmail(), request.getPassword());
            String token = jwtTokenProvider.generateToken(admin.getId(), admin.getEmail(), admin.getRole().toString());

            AuthResponse response = AuthResponse.builder()
                    .token(token)
                    .userId(admin.getId())
                    .email(admin.getEmail())
                    .role(admin.getRole().toString())
                    .build();

            log.info("Admin login successful: {}", request.getEmail());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Admin login failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("/account/balance")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountBalanceResponse> getAccountBalance() {
        try {
            String adminEmail = authenticationUtils.getAuthenticatedUserEmail();
            log.info("Admin {} requested account balance", adminEmail);

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

            log.info("Account balance retrieved for admin {}: Deposits: {}, Withdrawals: {}, Fees: {}, Holdings: {}",
                    adminEmail, totalDeposits, totalWithdrawals, totalFees, firmHoldings);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting account balance", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/fees")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FeeRateResponse> updateFeeRate(
            @Valid @RequestBody UpdateFeeRateRequest request,
            HttpServletRequest httpServletRequest) {

        try {
            String adminEmail = authenticationUtils.getAuthenticatedUserEmail();
            Long adminId = userService.getUserByEmail(adminEmail).getId();

            log.info("Admin {} is updating fee rate for pair: {}", adminEmail, request.getCurrencyPair());

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

            log.info("Fee rate updated successfully for pair: {} by admin: {}", request.getCurrencyPair(), adminEmail);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error updating fee rate", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/fees")
    @PreAuthorize("hasRole('ADMIN')")
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

            log.info("Retrieved {} fee rates", responses.size());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error getting fee rates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/reconciliation/trades/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TradeReconciliationRunResponse> runTradeReconciliation(
            @RequestParam(defaultValue = "24") int lookbackHours,
            HttpServletRequest httpServletRequest) {
        try {
            if (lookbackHours <= 0 || lookbackHours > 24 * 30) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            String adminEmail = authenticationUtils.getAuthenticatedUserEmail();
            Long adminId = userService.getUserByEmail(adminEmail).getId();
            LocalDateTime scopeTo = LocalDateTime.now();
            LocalDateTime scopeFrom = scopeTo.minusHours(lookbackHours);

            TradeReconciliationRunResponse response =
                    tradeReconciliationService.runTradeReconciliation(scopeFrom, scopeTo);

            HashMap<String, Object> auditChange = new HashMap<>();
            auditChange.put("runId", response.getId());
            auditChange.put("lookbackHours", lookbackHours);
            auditChange.put("scopeFrom", scopeFrom);
            auditChange.put("scopeTo", scopeTo);
            auditChange.put("totalIssues", response.getTotalIssues());
            adminService.recordAuditAction(
                    adminId,
                    "RUN_TRADE_RECONCILIATION",
                    "trade_reconciliation_runs",
                    auditChange,
                    httpServletRequest.getRemoteAddr()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error running trade reconciliation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/reconciliation/trades/runs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TradeReconciliationRunResponse>> listTradeReconciliationRuns() {
        try {
            return ResponseEntity.ok(tradeReconciliationService.getRecentRuns());
        } catch (Exception e) {
            log.error("Error listing trade reconciliation runs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/reconciliation/trades/runs/{runId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TradeReconciliationRunResponse> getTradeReconciliationRun(@PathVariable Long runId) {
        try {
            return ResponseEntity.ok(tradeReconciliationService.getRunDetails(runId));
        } catch (ReconciliationNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            log.error("Error loading trade reconciliation run {}", runId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/reconciliation/trades/runs/{runId}/repair")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TradeReconciliationRunResponse> repairTradeReconciliationRun(
            @PathVariable Long runId,
            HttpServletRequest httpServletRequest) {
        try {
            String adminEmail = authenticationUtils.getAuthenticatedUserEmail();
            Long adminId = userService.getUserByEmail(adminEmail).getId();
            TradeReconciliationRunResponse response = tradeReconciliationService.repairRun(runId);

            HashMap<String, Object> auditChange = new HashMap<>();
            auditChange.put("runId", runId);
            auditChange.put("status", response.getStatus());
            auditChange.put("autoRepairedIssues", response.getAutoRepairedIssues());
            adminService.recordAuditAction(
                    adminId,
                    "REPAIR_TRADE_RECONCILIATION",
                    "trade_reconciliation_runs",
                    auditChange,
                    httpServletRequest.getRemoteAddr()
            );

            return ResponseEntity.ok(response);
        } catch (ReconciliationNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            log.error("Error repairing trade reconciliation run {}", runId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

