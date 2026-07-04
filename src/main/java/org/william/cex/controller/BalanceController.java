package org.william.cex.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.william.cex.dto.request.AddBalanceRequest;
import org.william.cex.dto.response.BalanceResponse;
import org.william.cex.entity.UserWallet;
import org.william.cex.service.UserService;
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
        Long userId = authenticationUtils.getAuthenticatedUserId();

        log.info("User {} is adding {} {} to their balance", userId, request.getAmount(), request.getCurrency());

        userService.addBalance(userId, request.getCurrency(), request.getAmount());
        UserWallet wallet = userService.getWallet(userId, request.getCurrency());

        BalanceResponse response = BalanceResponse.builder()
                .userId(userId)
                .currency(request.getCurrency().toUpperCase())
                .balance(wallet.getBalance())
                .lockedAmount(wallet.getLockedAmount())
                .availableBalance(wallet.getAvailableBalance())
                .build();

        log.info("Balance added successfully for user {}: {} {} now has balance of {}",
                userId, request.getAmount(), request.getCurrency(), wallet.getBalance());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{currency}")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable String currency) {
        Long userId = authenticationUtils.getAuthenticatedUserId();

        log.info("User {} requested balance for {}", userId, currency);

        UserWallet wallet = userService.getWallet(userId, currency);

        BalanceResponse response = BalanceResponse.builder()
                .userId(userId)
                .currency(currency.toUpperCase())
                .balance(wallet.getBalance())
                .lockedAmount(wallet.getLockedAmount())
                .availableBalance(wallet.getAvailableBalance())
                .build();

        log.info("Balance retrieved for user {}: {} balance = {}, locked = {}, available = {}",
                userId, currency, wallet.getBalance(), wallet.getLockedAmount(), wallet.getAvailableBalance());
        return ResponseEntity.ok(response);
    }
}

