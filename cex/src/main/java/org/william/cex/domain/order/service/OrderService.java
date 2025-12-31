package org.william.cex.domain.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.william.cex.api.exception.InvalidOrderException;
import org.william.cex.domain.fee.service.FeeService;
import org.william.cex.domain.order.entity.Order;
import org.william.cex.domain.order.entity.OrderEvent;
import org.william.cex.domain.order.repository.OrderEventRepository;
import org.william.cex.domain.order.repository.OrderRepository;
import org.william.cex.domain.user.service.UserService;
import org.william.cex.infrastructure.cache.CacheManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@Slf4j
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderEventRepository orderEventRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private FeeService feeService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional
    public Order createOrder(Long userId, Order.OrderType orderType, String baseCurrency,
                            String quoteCurrency, BigDecimal amount, BigDecimal price) {

        // Validate order
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderException("Amount and price must be greater than 0");
        }

        // Lock balance based on order type
        String lockCurrency = orderType == Order.OrderType.BUY ? quoteCurrency : baseCurrency;
        BigDecimal lockAmount = orderType == Order.OrderType.BUY ?
                amount.multiply(price) : amount;

        userService.lockBalance(userId, lockCurrency, lockAmount);

        // Create order
        Order order = Order.builder()
                .userId(userId)
                .orderType(orderType)
                .baseCurrency(baseCurrency)
                .quoteCurrency(quoteCurrency)
                .amount(amount)
                .price(price)
                .filledAmount(BigDecimal.ZERO)
                .status(Order.OrderStatus.PENDING)
                .build();

        order = orderRepository.save(order);

        // Record event
        recordOrderEvent(order.getId(), OrderEvent.EventType.CREATED,
                "Order created: " + orderType + " " + amount + " " + baseCurrency);

        log.info("Order created: {} for user {}", order.getId(), userId);
        return order;
    }

    public Order getOrder(Long orderId) {
        // Try cache first
        Object cached = cacheManager.getOrder(orderId);
        if (cached instanceof Order) {
            return (Order) cached;
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new InvalidOrderException("Order not found: " + orderId));

        // Cache for 1 hour
        cacheManager.setOrder(orderId, order, 60);
        return order;
    }

    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = getOrder(orderId);

        if (order.getStatus() == Order.OrderStatus.CANCELLED) {
            throw new InvalidOrderException("Order already cancelled");
        }

        if (order.getStatus() == Order.OrderStatus.FILLED) {
            throw new InvalidOrderException("Cannot cancel filled order");
        }

        // Unlock balance
        String unlockCurrency = order.getOrderType() == Order.OrderType.BUY ?
                order.getQuoteCurrency() : order.getBaseCurrency();
        BigDecimal remainingAmount = order.getRemainingAmount();
        BigDecimal unlockAmount = order.getOrderType() == Order.OrderType.BUY ?
                remainingAmount.multiply(order.getPrice()) : remainingAmount;

        userService.unlockBalance(order.getUserId(), unlockCurrency, unlockAmount);

        order.setStatus(Order.OrderStatus.CANCELLED);
        orderRepository.save(order);

        recordOrderEvent(orderId, OrderEvent.EventType.CANCELLED, "Order cancelled by user");

        cacheManager.clearOrder(orderId);
        log.info("Order cancelled: {}", orderId);
    }

    @Transactional
    public void fillOrder(Long orderId, BigDecimal filledAmount) {
        Order order = getOrder(orderId);

        if (order.getStatus() == Order.OrderStatus.CANCELLED) {
            throw new InvalidOrderException("Cannot fill cancelled order");
        }

        order.setFilledAmount(order.getFilledAmount().add(filledAmount));

        if (order.isFullyFilled()) {
            order.setStatus(Order.OrderStatus.FILLED);
            recordOrderEvent(orderId, OrderEvent.EventType.FILLED, "Order fully filled");
        } else {
            order.setStatus(Order.OrderStatus.PARTIALLY_FILLED);
            recordOrderEvent(orderId, OrderEvent.EventType.PARTIALLY_FILLED,
                    "Order partially filled: " + filledAmount);
        }

        orderRepository.save(order);
        cacheManager.clearOrder(orderId);
        log.info("Order filled: {} amount: {}", orderId, filledAmount);
    }

    private void recordOrderEvent(Long orderId, OrderEvent.EventType eventType, String details) {
        try {
            OrderEvent event = OrderEvent.builder()
                    .orderId(orderId)
                    .eventType(eventType)
                    .details(objectMapper.valueToTree(details))
                    .build();
            orderEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to record order event", e);
        }
    }
}

