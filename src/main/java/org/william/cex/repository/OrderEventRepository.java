package org.william.cex.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.william.cex.entity.OrderEvent;

import java.util.List;

@Repository
public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {
    List<OrderEvent> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}

