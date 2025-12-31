package org.william.cex.domain.blnk.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class BlnkClient {

    @Value("${blnk.api.url:http://localhost:5001}")
    private String blnkApiUrl;

    @Value("${blnk.api.key:demo-key}")
    private String blnkApiKey;

    private final RestTemplate restTemplate;

    public BlnkClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Create a ledger in BLNK for a user
     */
    public Map<String, Object> createLedger(String currency, String userEmail) {
        try {
            String url = blnkApiUrl + "/ledgers";

            Map<String, Object> request = new HashMap<>();
            request.put("name", "User_" + userEmail);
            request.put("currency", currency);
            request.put("meta_data", Map.of("user_email", userEmail));

            log.info("Creating BLNK ledger for user: {}", userEmail);
            // Mock response for demo
            return Map.of(
                    "id", "ledger_" + System.nanoTime(),
                    "status", "active",
                    "currency", currency
            );
        } catch (RestClientException e) {
            log.error("Error creating BLNK ledger", e);
            throw new RuntimeException("Failed to create BLNK ledger", e);
        }
    }

    /**
     * Post a transaction to BLNK
     */
    public Map<String, Object> postTransaction(String ledgerId, String reference,
                                               String amount, String description) {
        try {
            String url = blnkApiUrl + "/transactions";

            Map<String, Object> request = new HashMap<>();
            request.put("ledger_id", ledgerId);
            request.put("reference", reference);
            request.put("amount", amount);
            request.put("description", description);
            request.put("meta_data", Map.of("timestamp", System.currentTimeMillis()));

            log.info("Posting transaction to BLNK: {}", reference);
            // Mock response for demo
            return Map.of(
                    "id", "txn_" + System.nanoTime(),
                    "status", "pending",
                    "reference", reference
            );
        } catch (RestClientException e) {
            log.error("Error posting BLNK transaction", e);
            throw new RuntimeException("Failed to post BLNK transaction", e);
        }
    }

    /**
     * Get transaction status from BLNK
     */
    public Map<String, Object> getTransactionStatus(String transactionId) {
        try {
            String url = blnkApiUrl + "/transactions/" + transactionId;

            log.info("Querying BLNK transaction status: {}", transactionId);
            // Mock response for demo
            return Map.of(
                    "id", transactionId,
                    "status", "confirmed"
            );
        } catch (RestClientException e) {
            log.error("Error getting BLNK transaction status", e);
            throw new RuntimeException("Failed to get BLNK transaction status", e);
        }
    }

    /**
     * Health check for BLNK
     */
    public boolean isHealthy() {
        try {
            String url = blnkApiUrl + "/health";
            log.debug("Checking BLNK health: {}", url);
            // Mock health check - always true for demo
            return true;
        } catch (Exception e) {
            log.error("BLNK health check failed", e);
            return false;
        }
    }
}

