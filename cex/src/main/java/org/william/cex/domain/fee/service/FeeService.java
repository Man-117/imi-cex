package org.william.cex.domain.fee.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.william.cex.domain.fee.entity.FeeRate;
import org.william.cex.domain.fee.entity.FeeTransaction;
import org.william.cex.domain.fee.repository.FeeRateRepository;
import org.william.cex.domain.fee.repository.FeeTransactionRepository;
import org.william.cex.infrastructure.cache.CacheManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class FeeService {

    @Autowired
    private FeeRateRepository feeRateRepository;

    @Autowired
    private FeeTransactionRepository feeTransactionRepository;

    @Autowired
    private CacheManager cacheManager;

    public FeeRate getFeeRate(String currencyPair) {
        // Try cache first
        Object cached = cacheManager.getFeeRate(currencyPair);
        if (cached instanceof FeeRate) {
            return (FeeRate) cached;
        }

        FeeRate feeRate = feeRateRepository.findLatestByCurrencyPair(currencyPair, LocalDateTime.now())
                .orElseGet(() -> {
                    // Default fee rate if not found
                    FeeRate defaultRate = FeeRate.builder()
                            .currencyPair(currencyPair)
                            .feePercentage(new BigDecimal("0.001")) // 0.1%
                            .effectiveFrom(LocalDateTime.now())
                            .build();
                    return feeRateRepository.save(defaultRate);
                });

        // Cache for 1 hour
        cacheManager.setFeeRate(currencyPair, feeRate, 60);
        return feeRate;
    }

    @Transactional
    public FeeRate updateFeeRate(String currencyPair, BigDecimal feePercentage, Long adminId) {
        if (feePercentage.compareTo(BigDecimal.ZERO) < 0 || feePercentage.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Fee percentage must be between 0 and 1");
        }

        FeeRate feeRate = FeeRate.builder()
                .currencyPair(currencyPair)
                .feePercentage(feePercentage)
                .effectiveFrom(LocalDateTime.now())
                .adminId(adminId)
                .build();

        feeRate = feeRateRepository.save(feeRate);

        // Invalidate cache
        cacheManager.clearFeeRates();

        log.info("Fee rate updated for pair {}: {}", currencyPair, feePercentage);
        return feeRate;
    }

    @Transactional
    public void recordFeeTransaction(Long orderId, BigDecimal feeAmount, FeeTransaction.FeeType feeType) {
        FeeTransaction transaction = FeeTransaction.builder()
                .orderId(orderId)
                .amount(feeAmount)
                .feeType(feeType)
                .build();

        feeTransactionRepository.save(transaction);
        log.info("Fee transaction recorded for order {}: {} {}", orderId, feeAmount, feeType);
    }

    public BigDecimal getTotalFees() {
        BigDecimal total = feeTransactionRepository.getTotalFees();
        return total != null ? total : BigDecimal.ZERO;
    }

    public List<FeeRate> getAllFeeRates() {
        return feeRateRepository.findAll();
    }
}

