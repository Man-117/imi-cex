package org.william.cex.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.william.cex.entity.IdempotencyKey;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {
    Optional<IdempotencyKey> findByIdempotencyKey(String idempotencyKey);
    void deleteByExpiresAtBefore(LocalDateTime expiresAt);
}
