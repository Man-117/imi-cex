package org.william.cex.api.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/v1/market")
@Slf4j
public class MarketController {

    @GetMapping("/price/{pair}")
    public ResponseEntity<Map<String, Object>> getMarketPrice(String pair) {
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

