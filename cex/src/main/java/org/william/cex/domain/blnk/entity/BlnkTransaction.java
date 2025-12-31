package org.william.cex.domain.blnk.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "blnk_transactions", indexes = {
        @Index(name = "idx_blnk_transactions_status", columnList = "status"),
        @Index(name = "idx_blnk_transactions_created_at", columnList = "created_at DESC"),
        @Index(name = "idx_blnk_transactions_order_id", columnList = "local_order_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlnkTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long localOrderId;

    @Column(length = 255)
    private String blnkTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(columnDefinition = "JSONB")
    private JsonNode responseData;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = Status.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Status {
        PENDING, CONFIRMED, FAILED
    }
}

