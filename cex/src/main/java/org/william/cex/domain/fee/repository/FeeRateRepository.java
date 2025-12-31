package org.william.cex.domain.fee.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.william.cex.domain.fee.entity.FeeRate;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface FeeRateRepository extends JpaRepository<FeeRate, Long> {
    @Query("SELECT f FROM FeeRate f WHERE f.currencyPair = :pair AND f.effectiveFrom <= :now ORDER BY f.effectiveFrom DESC LIMIT 1")
    Optional<FeeRate> findLatestByCurrencyPair(String pair, LocalDateTime now);
}

