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

@RestController
@RequestMapping("/v1/balance")
@Slf4j
public class BalanceController {

    @Autowired
    private UserService userService;

    @PostMapping("/add")
    public ResponseEntity<BalanceResponse> addBalance(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody AddBalanceRequest request) {

        try {
            // For demo/imitation environment: extract user ID from header (format: Bearer {userId})
            Long userId = extractUserIdFromAuth(authHeader);

            userService.addBalance(userId, request.getCurrency(), request.getAmount());
            UserWallet wallet = userService.getWallet(userId, request.getCurrency());

            BalanceResponse response = BalanceResponse.builder()
                    .userId(userId)
                    .currency(request.getCurrency())
                    .balance(wallet.getBalance())
                    .lockedAmount(wallet.getLockedAmount())
                    .availableBalance(wallet.getAvailableBalance())
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error adding balance", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{currency}")
    public ResponseEntity<BalanceResponse> getBalance(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String currency) {

        try {
            Long userId = extractUserIdFromAuth(authHeader);
            UserWallet wallet = userService.getWallet(userId, currency);

            BalanceResponse response = BalanceResponse.builder()
                    .userId(userId)
                    .currency(currency)
                    .balance(wallet.getBalance())
                    .lockedAmount(wallet.getLockedAmount())
                    .availableBalance(wallet.getAvailableBalance())
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting balance", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
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

