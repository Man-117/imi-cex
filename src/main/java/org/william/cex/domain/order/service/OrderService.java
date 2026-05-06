package org.william.cex.domain.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.william.cex.api.exception.InvalidOrderException;
import org.william.cex.api.exception.RiskLimitExceededException;
import org.william.cex.domain.fee.entity.FeeTransaction;
import org.william.cex.domain.fee.service.FeeService;
import org.william.cex.domain.order.entity.Order;
import org.william.cex.domain.order.entity.OrderEvent;
import org.william.cex.domain.order.entity.Trade;
import org.william.cex.domain.order.repository.OrderEventRepository;
import org.william.cex.domain.order.repository.OrderRepository;
import org.william.cex.domain.order.repository.TradeRepository;
import org.william.cex.domain.user.service.UserService;
import org.william.cex.infrastructure.cache.CacheManager;
import org.william.cex.infrastructure.metrics.TradingMetricsService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

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
    private TradeRepository tradeRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private TradingMetricsService tradingMetricsService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final int MAX_MATCH_CANDIDATES = 100;

    @Value("${cex.risk.max-order-notional:500000}")
    private BigDecimal maxOrderNotional;

    @Value("${cex.risk.max-daily-notional:2000000}")
    private BigDecimal maxDailyNotional;

    @Transactional
    public Order createOrder(Long userId, Order.OrderType orderType, String baseCurrency,
                            String quoteCurrency, BigDecimal amount, BigDecimal price) {
        String normalizedBase = normalizeCurrency(baseCurrency);
        String normalizedQuote = normalizeCurrency(quoteCurrency);
        BigDecimal normalizedAmount = scale(amount);
        BigDecimal normalizedPrice = scale(price);

        // Validate order
        if (normalizedAmount.compareTo(BigDecimal.ZERO) <= 0 || normalizedPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderException("Amount and price must be greater than 0");
        }
        validateRiskLimits(userId, normalizedAmount, normalizedPrice);

        // Lock balance based on order type
        String lockCurrency = orderType == Order.OrderType.BUY ? normalizedQuote : normalizedBase;
        BigDecimal lockAmount = orderType == Order.OrderType.BUY ?
                scale(normalizedAmount.multiply(normalizedPrice)) : normalizedAmount;

        userService.lockBalance(userId, lockCurrency, lockAmount);

        // Create order
        Order order = Order.builder()
                .userId(userId)
                .orderType(orderType)
                .baseCurrency(normalizedBase)
                .quoteCurrency(normalizedQuote)
                .amount(normalizedAmount)
                .price(normalizedPrice)
                .filledAmount(BigDecimal.ZERO)
                .status(Order.OrderStatus.PENDING)
                .build();

        order = orderRepository.save(order);

        // Record event
        recordOrderEvent(order.getId(), OrderEvent.EventType.CREATED,
                "Order created: " + orderType + " " + normalizedAmount + " " + normalizedBase);

        tradingMetricsService.incrementOrdersCreated();
        tryMatchOrder(order);

        log.info("Order created: {} for user {}", order.getId(), userId);
        return orderRepository.findById(order.getId()).orElse(order);
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
        tradingMetricsService.incrementOrdersCancelled();
        log.info("Order cancelled: {}", orderId);
    }

    @Transactional
    public void fillOrder(Long orderId, BigDecimal filledAmount) {
        Order order = getOrder(orderId);

        if (order.getStatus() == Order.OrderStatus.CANCELLED) {
            throw new InvalidOrderException("Cannot fill cancelled order");
        }

        applyOrderFill(order, scale(filledAmount), "Manual fill");

        orderRepository.save(order);
        cacheManager.clearOrder(orderId);
        log.info("Order filled: {} amount: {}", orderId, filledAmount);
    }

    private void tryMatchOrder(Order takerOrder) {
        if (takerOrder.getStatus() == Order.OrderStatus.CANCELLED || takerOrder.getStatus() == Order.OrderStatus.FILLED) {
            return;
        }

        Pageable pageable = PageRequest.of(0, MAX_MATCH_CANDIDATES);
        List<Order> candidates = takerOrder.getOrderType() == Order.OrderType.BUY
                ? orderRepository.findMatchableSellOrders(
                        takerOrder.getBaseCurrency(),
                        takerOrder.getQuoteCurrency(),
                        takerOrder.getPrice(),
                        takerOrder.getId(),
                        pageable)
                : orderRepository.findMatchableBuyOrders(
                        takerOrder.getBaseCurrency(),
                        takerOrder.getQuoteCurrency(),
                        takerOrder.getPrice(),
                        takerOrder.getId(),
                        pageable);

        for (Order makerOrder : candidates) {
            if (takerOrder.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            if (makerOrder.getStatus() == Order.OrderStatus.CANCELLED || makerOrder.getStatus() == Order.OrderStatus.FILLED) {
                continue;
            }
            if (makerOrder.getUserId().equals(takerOrder.getUserId())) {
                tradingMetricsService.incrementSelfMatchSkipped();
                continue;
            }

            BigDecimal tradeAmount = takerOrder.getRemainingAmount().min(makerOrder.getRemainingAmount());
            if (tradeAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal executionPrice = makerOrder.getPrice();
            executeTrade(takerOrder, makerOrder, tradeAmount, executionPrice);
        }
    }

    private void executeTrade(Order takerOrder, Order makerOrder, BigDecimal amount, BigDecimal executionPrice) {
        BigDecimal normalizedAmount = scale(amount);
        BigDecimal normalizedExecutionPrice = scale(executionPrice);

        Order buyOrder = takerOrder.getOrderType() == Order.OrderType.BUY ? takerOrder : makerOrder;
        Order sellOrder = takerOrder.getOrderType() == Order.OrderType.SELL ? takerOrder : makerOrder;

        BigDecimal tradeNotional = scale(normalizedAmount.multiply(normalizedExecutionPrice));
        String pair = buyOrder.getBaseCurrency() + "/" + buyOrder.getQuoteCurrency();
        BigDecimal buyFee = feeService.calculateTradingFee(pair, tradeNotional);
        BigDecimal sellFee = feeService.calculateTradingFee(pair, tradeNotional);

        userService.settleBuyTrade(
                buyOrder.getUserId(),
                buyOrder.getBaseCurrency(),
                buyOrder.getQuoteCurrency(),
                normalizedAmount,
                normalizedExecutionPrice,
                buyOrder.getPrice(),
                buyFee
        );
        userService.settleSellTrade(
                sellOrder.getUserId(),
                sellOrder.getBaseCurrency(),
                sellOrder.getQuoteCurrency(),
                normalizedAmount,
                normalizedExecutionPrice,
                sellFee
        );

        Trade trade = Trade.builder()
                .buyOrderId(buyOrder.getId())
                .sellOrderId(sellOrder.getId())
                .amount(normalizedAmount)
                .price(normalizedExecutionPrice)
                .settlementStatus("SETTLED")
                .settledAt(LocalDateTime.now())
                .build();
        tradeRepository.save(trade);

        applyOrderFill(buyOrder, normalizedAmount, "Trade executed with order " + sellOrder.getId());
        applyOrderFill(sellOrder, normalizedAmount, "Trade executed with order " + buyOrder.getId());

        orderRepository.save(buyOrder);
        orderRepository.save(sellOrder);
        cacheManager.clearOrder(buyOrder.getId());
        cacheManager.clearOrder(sellOrder.getId());

        feeService.recordFeeTransaction(buyOrder.getId(), buyFee, FeeTransaction.FeeType.TRADING_FEE);
        feeService.recordFeeTransaction(sellOrder.getId(), sellFee, FeeTransaction.FeeType.TRADING_FEE);

        tradingMetricsService.incrementTradesExecuted();
        log.info("Trade executed buyOrder={} sellOrder={} amount={} price={}",
                buyOrder.getId(), sellOrder.getId(), normalizedAmount, normalizedExecutionPrice);
    }

    private void applyOrderFill(Order order, BigDecimal fillAmount, String details) {
        order.setFilledAmount(scale(order.getFilledAmount().add(fillAmount)));
        if (order.isFullyFilled()) {
            order.setStatus(Order.OrderStatus.FILLED);
            recordOrderEvent(order.getId(), OrderEvent.EventType.FILLED, details);
            tradingMetricsService.incrementOrdersFilled();
        } else {
            order.setStatus(Order.OrderStatus.PARTIALLY_FILLED);
            recordOrderEvent(order.getId(), OrderEvent.EventType.PARTIALLY_FILLED, details);
            tradingMetricsService.incrementOrdersPartiallyFilled();
        }
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

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(8, RoundingMode.HALF_UP);
    }

    private String normalizeCurrency(String currency) {
        return currency.toUpperCase();
    }

    private void validateRiskLimits(Long userId, BigDecimal amount, BigDecimal price) {
        BigDecimal orderNotional = scale(amount.multiply(price));
        if (orderNotional.compareTo(maxOrderNotional) > 0) {
            throw new RiskLimitExceededException(
                    "Order notional exceeds limit of " + maxOrderNotional);
        }

        BigDecimal dailyNotional = orderRepository.getDailyTradedNotional(userId, LocalDateTime.now().minusDays(1));
        if (dailyNotional == null) {
            dailyNotional = BigDecimal.ZERO;
        }
        BigDecimal projectedNotional = scale(dailyNotional.add(orderNotional));
        if (projectedNotional.compareTo(maxDailyNotional) > 0) {
            throw new RiskLimitExceededException(
                    "Daily notional exceeds limit of " + maxDailyNotional);
        }
    }
}

