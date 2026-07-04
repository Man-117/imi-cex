package org.william.cex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.william.cex.exception.InvalidOrderException;
import org.william.cex.exception.RiskLimitExceededException;
import org.william.cex.entity.Order;
import org.william.cex.entity.OrderEvent;
import org.william.cex.entity.Trade;
import org.william.cex.repository.OrderEventRepository;
import org.william.cex.repository.OrderRepository;
import org.william.cex.repository.TradeRepository;
import org.william.cex.infrastructure.cache.CacheManager;
import org.william.cex.infrastructure.metrics.TradingMetricsService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.annotation.PostConstruct;

import com.orderbook.OrderBook;
import com.orderbook.entity.LimitOrder;
import com.orderbook.entity.GeneralOrderInfo;
import com.orderbook.entity.Side;

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

    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();

    private OrderBook getOrderBook(String pair) {
        return orderBooks.computeIfAbsent(pair, k -> OrderBook.builder(k).build());
    }

    @PostConstruct
    public void initOrderBooks() {
        List<Order> openOrders = new java.util.ArrayList<>();
        openOrders.addAll(orderRepository.findByStatus(Order.OrderStatus.PENDING));
        openOrders.addAll(orderRepository.findByStatus(Order.OrderStatus.PARTIALLY_FILLED));
        
        openOrders.sort(java.util.Comparator.comparing(Order::getCreatedAt));
        
        for (Order order : openOrders) {
            String pair = order.getBaseCurrency() + "/" + order.getQuoteCurrency();
            OrderBook ob = getOrderBook(pair);
            ob.addOrder(toLimitOrder(order));
        }
    }

    private long toLong(BigDecimal value) {
        return value.multiply(BigDecimal.valueOf(100000000)).longValue();
    }

    private BigDecimal toBigDecimal(long value) {
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(100000000), 8, RoundingMode.HALF_UP);
    }

    private LimitOrder toLimitOrder(Order order) {
        UUID orderUuid = new UUID(0L, order.getId());
        UUID userUuid = new UUID(0L, order.getUserId());
        Side side = order.getOrderType() == Order.OrderType.BUY ? Side.BUY : Side.SELL;
        
        GeneralOrderInfo info = new GeneralOrderInfo(
                orderUuid,
                com.orderbook.entity.OrderType.LIMIT,
                side,
                toLong(order.getRemainingAmount()),
                userUuid,
                order.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        );
        return new LimitOrder(info, toLong(order.getPrice()));
    }

    @Transactional(rollbackFor = Exception.class)
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

    @Transactional(rollbackFor = Exception.class)
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

        // Cancel in OrderBook
        String pair = order.getBaseCurrency() + "/" + order.getQuoteCurrency();
        OrderBook orderBook = getOrderBook(pair);
        orderBook.cancelOrder(toLimitOrder(order));

        order.setStatus(Order.OrderStatus.CANCELLED);
        orderRepository.save(order);

        recordOrderEvent(orderId, OrderEvent.EventType.CANCELLED, "Order cancelled by user");

        cacheManager.clearOrder(orderId);
        tradingMetricsService.incrementOrdersCancelled();
        log.info("Order cancelled: {}", orderId);
    }


    private void tryMatchOrder(Order takerOrder) {
        if (takerOrder.getStatus() == Order.OrderStatus.CANCELLED || takerOrder.getStatus() == Order.OrderStatus.FILLED) {
            return;
        }

        String pair = takerOrder.getBaseCurrency() + "/" + takerOrder.getQuoteCurrency();
        OrderBook orderBook = getOrderBook(pair);
        orderBook.addOrder(toLimitOrder(takerOrder));

        com.orderbook.entity.Trade trade;
        while ((trade = orderBook.consumeResult()) != null) {
            Long takerId = trade.takerId().getLeastSignificantBits();
            Long makerId = trade.makerId().getLeastSignificantBits();

            Order taker = getOrder(takerId);
            Order maker = getOrder(makerId);

            if (taker.getUserId().equals(maker.getUserId())) {
                tradingMetricsService.incrementSelfMatchSkipped();
            }

            BigDecimal tradeAmount = toBigDecimal(trade.quantity());
            BigDecimal executionPrice = toBigDecimal(trade.price());

            executeTrade(taker, maker, tradeAmount, executionPrice);
        }
    }

    private void executeTrade(Order takerOrder, Order makerOrder, BigDecimal amount, BigDecimal executionPrice) {
        BigDecimal normalizedAmount = scale(amount);
        BigDecimal normalizedExecutionPrice = scale(executionPrice);

        Order buyOrder = takerOrder.getOrderType() == Order.OrderType.BUY ? takerOrder : makerOrder;
        Order sellOrder = takerOrder.getOrderType() == Order.OrderType.SELL ? takerOrder : makerOrder;

        BigDecimal tradeNotional = scale(normalizedAmount.multiply(normalizedExecutionPrice));
        String pair = buyOrder.getBaseCurrency() + "/" + buyOrder.getQuoteCurrency();
        BigDecimal buyFee = BigDecimal.ZERO;
        BigDecimal sellFee = BigDecimal.ZERO;

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

