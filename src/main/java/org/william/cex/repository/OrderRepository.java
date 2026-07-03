package org.william.cex.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.william.cex.entity.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long userId, Pageable pageable);
    List<Order> findByStatus(Order.OrderStatus status);

    @Query("""
            SELECT o
            FROM Order o
            WHERE o.orderType = 'SELL'
              AND o.baseCurrency = :baseCurrency
              AND o.quoteCurrency = :quoteCurrency
              AND o.status IN ('PENDING', 'PARTIALLY_FILLED')
              AND o.price <= :maxPrice
              AND o.id <> :excludeOrderId
            ORDER BY o.price ASC, o.createdAt ASC
            """)
    List<Order> findMatchableSellOrders(@Param("baseCurrency") String baseCurrency,
                                        @Param("quoteCurrency") String quoteCurrency,
                                        @Param("maxPrice") BigDecimal maxPrice,
                                        @Param("excludeOrderId") Long excludeOrderId,
                                        Pageable pageable);

    @Query("""
            SELECT o
            FROM Order o
            WHERE o.orderType = 'BUY'
              AND o.baseCurrency = :baseCurrency
              AND o.quoteCurrency = :quoteCurrency
              AND o.status IN ('PENDING', 'PARTIALLY_FILLED')
              AND o.price >= :minPrice
              AND o.id <> :excludeOrderId
            ORDER BY o.price DESC, o.createdAt ASC
            """)
    List<Order> findMatchableBuyOrders(@Param("baseCurrency") String baseCurrency,
                                       @Param("quoteCurrency") String quoteCurrency,
                                       @Param("minPrice") BigDecimal minPrice,
                                       @Param("excludeOrderId") Long excludeOrderId,
                                       Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(o.amount * o.price), 0)
            FROM Order o
            WHERE o.userId = :userId
              AND o.createdAt >= :fromTime
              AND o.status <> 'CANCELLED'
            """)
    BigDecimal getDailyTradedNotional(@Param("userId") Long userId, @Param("fromTime") LocalDateTime fromTime);
}

