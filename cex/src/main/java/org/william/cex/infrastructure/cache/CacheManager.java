package org.william.cex.infrastructure.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CacheManager {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String BALANCE_KEY = "balance:%d:%s";
    private static final String ORDER_KEY = "order:%d";
    private static final String FEE_RATE_KEY = "fee-rate:%s";
    private static final String IDEMPOTENCY_KEY = "idempotency:%s";

    public void setBalance(Long userId, String currency, Object balance, long ttlMinutes) {
        String key = String.format(BALANCE_KEY, userId, currency);
        redisTemplate.opsForValue().set(key, balance, ttlMinutes, TimeUnit.MINUTES);
        log.debug("Cache balance set for user {} currency {}", userId, currency);
    }

    public Object getBalance(Long userId, String currency) {
        String key = String.format(BALANCE_KEY, userId, currency);
        return redisTemplate.opsForValue().get(key);
    }

    public void clearBalance(Long userId, String currency) {
        String key = String.format(BALANCE_KEY, userId, currency);
        redisTemplate.delete(key);
    }

    public void clearAllBalances(Long userId) {
        String pattern = String.format(BALANCE_KEY, userId, "*");
        redisTemplate.delete(redisTemplate.keys(pattern));
    }

    public void setOrder(Long orderId, Object order, long ttlMinutes) {
        String key = String.format(ORDER_KEY, orderId);
        redisTemplate.opsForValue().set(key, order, ttlMinutes, TimeUnit.MINUTES);
    }

    public Object getOrder(Long orderId) {
        String key = String.format(ORDER_KEY, orderId);
        return redisTemplate.opsForValue().get(key);
    }

    public void clearOrder(Long orderId) {
        String key = String.format(ORDER_KEY, orderId);
        redisTemplate.delete(key);
    }

    public void setFeeRate(String currencyPair, Object feeRate, long ttlMinutes) {
        String key = String.format(FEE_RATE_KEY, currencyPair);
        redisTemplate.opsForValue().set(key, feeRate, ttlMinutes, TimeUnit.MINUTES);
    }

    public Object getFeeRate(String currencyPair) {
        String key = String.format(FEE_RATE_KEY, currencyPair);
        return redisTemplate.opsForValue().get(key);
    }

    public void clearFeeRates() {
        String pattern = String.format(FEE_RATE_KEY, "*");
        redisTemplate.delete(redisTemplate.keys(pattern));
    }

    public void setIdempotencyKey(String idempotencyKey, Object response, long ttlHours) {
        String key = String.format(IDEMPOTENCY_KEY, idempotencyKey);
        redisTemplate.opsForValue().set(key, response, ttlHours, TimeUnit.HOURS);
    }

    public Object getIdempotencyKey(String idempotencyKey) {
        String key = String.format(IDEMPOTENCY_KEY, idempotencyKey);
        return redisTemplate.opsForValue().get(key);
    }
}

