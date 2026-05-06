package org.william.cex.domain.admin.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_logs_admin_id", columnList = "admin_id"),
        @Index(name = "idx_audit_logs_created_at", columnList = "created_at DESC"),
        @Index(name = "idx_audit_logs_resource", columnList = "resource")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long adminId;

    @Column(nullable = false, length = 255)
    private String action;

    @Column(length = 255)
    private String resource;

    @Column(columnDefinition = "JSONB")
    @Type(JsonBinaryType.class)
    private JsonNode changes;

    @Column(length = 45)
    private String ipAddress;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

