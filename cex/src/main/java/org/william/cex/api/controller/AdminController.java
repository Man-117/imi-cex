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
import org.william.cex.domain.admin.service.AdminService;
import org.william.cex.domain.fee.entity.FeeRate;
import org.william.cex.domain.fee.service.FeeService;
import org.william.cex.domain.user.entity.User;
import org.william.cex.domain.user.entity.UserAccount;
import org.william.cex.domain.user.repository.UserAccountRepository;
import org.william.cex.domain.user.service.UserService;
import org.william.cex.infrastructure.security.AuthenticationUtils;
import org.william.cex.infrastructure.security.JwtTokenProvider;

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

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationUtils authenticationUtils;

    @Autowired
    private AdminService adminService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

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
            @Valid @RequestBody UpdateFeeRateRequest request) {

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

            log.info("Fee rate updated successfully for pair: {} by admin: {}", request.getCurrencyPair(), adminEmail);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error updating fee rate", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/fees")
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
}

