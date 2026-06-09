package org.william.cex.api.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.william.cex.api.dto.response.FeeRateResponse;
import org.william.cex.domain.fee.entity.FeeRate;
import org.william.cex.domain.fee.service.FeeService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/market")
@Slf4j
public class MarketController {

    @GetMapping("/price/{base}/{quote}")
    public ResponseEntity<Map<String, Object>> getMarketPrice(@PathVariable String base, @PathVariable String quote) {
        String pair = base.toUpperCase() + "/" + quote.toUpperCase();
        try {
            log.info("Requested market price for pair: {}", pair);

            // For demo/imitation environment, return simulated prices
            BigDecimal price = simulateMarketPrice(pair);

            Map<String, Object> response = new HashMap<>();
            response.put("pair", pair);
            response.put("price", price);
            response.put("timestamp", System.currentTimeMillis());

            log.info("Market price retrieved for {}: {}", pair, price);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting market price for pair: {}", pair, e);
            return ResponseEntity.notFound().build();
        }
    }

    private BigDecimal simulateMarketPrice(String pair) {
        // Simulated prices for demo
        return switch (pair.toUpperCase()) {
            case "BTC/USD" -> new BigDecimal("43500.50");
            case "ETH/USD" -> new BigDecimal("2450.75");
            case "XRP/USD" -> new BigDecimal("2.85");
            case "USDT/USD" -> new BigDecimal("1.00");
            default -> new BigDecimal("1000.00");
        };
    }
}

