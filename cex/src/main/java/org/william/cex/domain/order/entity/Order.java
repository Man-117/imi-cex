package org.william.cex.domain.order.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_user_id", columnList = "user_id"),
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_created_at", columnList = "created_at DESC"),
        @Index(name = "idx_orders_user_created", columnList = "user_id,created_at DESC")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType orderType;

    @Column(nullable = false, length = 10)
    private String baseCurrency;

    @Column(nullable = false, length = 10)
    private String quoteCurrency;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private BigDecimal filledAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (filledAmount == null) {
            filledAmount = BigDecimal.ZERO;
        }
        if (status == null) {
            status = OrderStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum OrderType {
        BUY, SELL
    }

    public enum OrderStatus {
        PENDING, FILLED, CANCELLED, PARTIALLY_FILLED
    }

    public BigDecimal getTotalValue() {
        return amount.multiply(price);
    }

    public BigDecimal getRemainingAmount() {
        return amount.subtract(filledAmount);
    }

    public boolean isFullyFilled() {
        return filledAmount.compareTo(amount) >= 0;
    }
}

