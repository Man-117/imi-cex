package org.william.cex.domain.fee.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fee_rates", indexes = {
        @Index(name = "idx_fee_rates_currency_pair", columnList = "currency_pair"),
        @Index(name = "idx_fee_rates_effective_from", columnList = "effective_from DESC")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String currencyPair;

    @Column(nullable = false)
    private BigDecimal feePercentage;

    @Column(nullable = false)
    private LocalDateTime effectiveFrom;

    private Long adminId;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (effectiveFrom == null) {
            effectiveFrom = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

