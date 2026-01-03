package org.william.cex;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple connectivity test to verify PostgreSQL and Redis are accessible
 */
@SpringBootTest
@Slf4j
class ConnectivityTest {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    @Test
    @DisplayName("Test PostgreSQL Connection")
    void testPostgreSQLConnection() {
        log.info("=== Testing PostgreSQL Connection ===");

        assertNotNull(jdbcTemplate, "JdbcTemplate should be autowired");

        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            assertEquals(1, result, "PostgreSQL should return 1");
            log.info("✅ PostgreSQL Connection: SUCCESS");
        } catch (Exception e) {
            log.error("❌ PostgreSQL Connection: FAILED - {}", e.getMessage());
            fail("PostgreSQL connection failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test Redis Connection")
    void testRedisConnection() {
        log.info("=== Testing Redis Connection ===");

        assertNotNull(redisTemplate, "RedisTemplate should be autowired");

        try {
            redisTemplate.opsForValue().set("test-key", "test-value");
            String value = redisTemplate.opsForValue().get("test-key");
            assertEquals("test-value", value, "Redis should return the test value");
            redisTemplate.delete("test-key");
            log.info("✅ Redis Connection: SUCCESS");
        } catch (Exception e) {
            log.error("❌ Redis Connection: FAILED - {}", e.getMessage());
            fail("Redis connection failed: " + e.getMessage());
        }
    }
}

