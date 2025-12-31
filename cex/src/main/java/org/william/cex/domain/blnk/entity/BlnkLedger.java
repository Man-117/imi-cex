package org.william.cex.domain.blnk.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "blnk_ledgers", indexes = {
        @Index(name = "idx_blnk_ledgers_user_id", columnList = "user_id"),
        @Index(name = "idx_blnk_ledgers_blnk_account_id", columnList = "blnk_account_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlnkLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false, unique = true, length = 255)
    private String blnkAccountId;

    @Column(nullable = false, length = 50)
    private String blnkStatus;

    private LocalDateTime syncedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (blnkStatus == null) {
            blnkStatus = "PENDING";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

