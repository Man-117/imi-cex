package org.william.cex.infrastructure.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.serializer.SerializationException;
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

    public void setBalance(Long userId, String currency, Object balance, long ttlMinutes) {
        String key = String.format(BALANCE_KEY, userId, currency);
        redisTemplate.opsForValue().set(key, balance, ttlMinutes, TimeUnit.MINUTES);
        log.debug("Cache balance set for user {} currency {}", userId, currency);
    }

    public Object getBalance(Long userId, String currency) {
        String key = String.format(BALANCE_KEY, userId, currency);
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (SerializationException e) {
            log.warn("Failed to deserialize cached balance key {}. Evicting corrupt entry.", key);
            redisTemplate.delete(key);
            return null;
        }
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
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (SerializationException e) {
            log.warn("Failed to deserialize cached order key {}. Evicting corrupt entry.", key);
            redisTemplate.delete(key);
            return null;
        }
    }

    public void clearOrder(Long orderId) {
        String key = String.format(ORDER_KEY, orderId);
        redisTemplate.delete(key);
    }


}

