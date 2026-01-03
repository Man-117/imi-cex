package org.william.cex.api.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.william.cex.api.dto.request.AddBalanceRequest;
import org.william.cex.api.dto.response.BalanceResponse;
import org.william.cex.domain.user.entity.UserWallet;
import org.william.cex.domain.user.service.UserService;
import org.william.cex.infrastructure.security.AuthenticationUtils;

@RestController
@RequestMapping("/v1/balance")
@Slf4j
public class BalanceController {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationUtils authenticationUtils;

    @PostMapping("/add")
    public ResponseEntity<BalanceResponse> addBalance(
            @Valid @RequestBody AddBalanceRequest request) {

        try {
            String userEmail = authenticationUtils.getAuthenticatedUserEmail();
            Long userId = userService.getUserByEmail(userEmail).getId();

            log.info("User {} is adding {} {} to their balance", userEmail, request.getAmount(), request.getCurrency());

            userService.addBalance(userId, request.getCurrency(), request.getAmount());
            UserWallet wallet = userService.getWallet(userId, request.getCurrency());

            BalanceResponse response = BalanceResponse.builder()
                    .userId(userId)
                    .currency(request.getCurrency())
                    .balance(wallet.getBalance())
                    .lockedAmount(wallet.getLockedAmount())
                    .availableBalance(wallet.getAvailableBalance())
                    .build();

            log.info("Balance added successfully for user {}: {} {} now has balance of {}",
                    userEmail, request.getAmount(), request.getCurrency(), wallet.getBalance());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error adding balance", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{currency}")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable String currency) {

        try {
            String userEmail = authenticationUtils.getAuthenticatedUserEmail();
            Long userId = userService.getUserByEmail(userEmail).getId();

            log.info("User {} requested balance for {}", userEmail, currency);

            UserWallet wallet = userService.getWallet(userId, currency);

            BalanceResponse response = BalanceResponse.builder()
                    .userId(userId)
                    .currency(currency)
                    .balance(wallet.getBalance())
                    .lockedAmount(wallet.getLockedAmount())
                    .availableBalance(wallet.getAvailableBalance())
                    .build();

            log.info("Balance retrieved for user {}: {} balance = {}, locked = {}, available = {}",
                    userEmail, currency, wallet.getBalance(), wallet.getLockedAmount(), wallet.getAvailableBalance());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting balance", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}

