package org.william.cex.domain.idempotency.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.william.cex.exception.IdempotencyConflictException;
import org.william.cex.domain.idempotency.entity.IdempotencyKey;
import org.william.cex.domain.idempotency.repository.IdempotencyKeyRepository;
import org.william.cex.infrastructure.cache.CacheManager;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyKeyRepository idempotencyKeyRepository,
                              CacheManager cacheManager,
                              ObjectMapper objectMapper) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
    }

    public <T> Optional<CachedResponse<T>> getCachedResponse(String key, Long userId, Class<T> bodyType) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        Object cached;
        try {
            cached = cacheManager.getIdempotencyKey(key);
        } catch (Exception e) {
            cached = null;
        }
        if (cached != null) {
            JsonNode payload = objectMapper.valueToTree(cached);
            CachedResponse<T> response = toCachedResponse(payload, userId, bodyType);
            return Optional.of(response);
        }

        Optional<IdempotencyKey> entryOpt = idempotencyKeyRepository.findByIdempotencyKey(key);
        if (entryOpt.isEmpty()) {
            return Optional.empty();
        }

        IdempotencyKey entry = entryOpt.get();
        if (entry.getExpiresAt().isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }

        CachedResponse<T> response = toCachedResponse(entry.getResponseData(), userId, bodyType);
        try {
            cacheManager.setIdempotencyKey(key, entry.getResponseData(), IDEMPOTENCY_TTL_HOURS);
        } catch (Exception ignored) {
            // Cache write failures should not block idempotent behavior backed by DB.
        }
        return Optional.of(response);
    }

    public void storeResponse(String key, Long userId, int statusCode, Object body) {
        if (key == null || key.isBlank()) {
            return;
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("userId", userId);
        payload.put("statusCode", statusCode);
        if (body == null) {
            payload.putNull("body");
        } else {
            payload.set("body", objectMapper.valueToTree(body));
        }

        IdempotencyKey entry = idempotencyKeyRepository.findByIdempotencyKey(key)
                .orElseGet(() -> IdempotencyKey.builder()
                        .idempotencyKey(key)
                        .userId(userId)
                        .build());

        if (!entry.getUserId().equals(userId)) {
            throw new IdempotencyConflictException("Idempotency key already used by another user");
        }

        entry.setResponseData(payload);
        entry.setExpiresAt(LocalDateTime.now().plusHours(IDEMPOTENCY_TTL_HOURS));
        idempotencyKeyRepository.save(entry);
        try {
            cacheManager.setIdempotencyKey(key, payload, IDEMPOTENCY_TTL_HOURS);
        } catch (Exception ignored) {
            // Persisted key remains source-of-truth if cache is unavailable.
        }
    }

    private <T> CachedResponse<T> toCachedResponse(JsonNode payload, Long userId, Class<T> bodyType) {
        long storedUserId = payload.path("userId").asLong(-1);
        if (storedUserId != userId) {
            throw new IdempotencyConflictException("Idempotency key already used by another user");
        }

        int statusCode = payload.path("statusCode").asInt(200);
        JsonNode bodyNode = payload.path("body");
        T body = null;
        if (!bodyNode.isMissingNode() && !bodyNode.isNull() && bodyType != Void.class) {
            body = objectMapper.convertValue(bodyNode, bodyType);
        }
        return new CachedResponse<>(statusCode, body);
    }

    public record CachedResponse<T>(int statusCode, T body) {
    }
}
