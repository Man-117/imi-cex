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
@Table(name = "administrators")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Administrator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(columnDefinition = "JSONB")
    @Type(JsonBinaryType.class)
    private JsonNode permissions;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

