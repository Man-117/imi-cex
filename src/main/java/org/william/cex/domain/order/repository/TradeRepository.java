package org.william.cex.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.william.cex.domain.order.entity.Trade;

import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {
    List<Trade> findByBuyOrderIdOrSellOrderId(Long buyOrderId, Long sellOrderId);
}

