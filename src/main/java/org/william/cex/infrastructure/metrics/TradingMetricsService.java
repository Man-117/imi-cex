package org.william.cex.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class TradingMetricsService {

    private final Counter ordersCreatedCounter;
    private final Counter ordersCancelledCounter;
    private final Counter ordersFilledCounter;
    private final Counter ordersPartiallyFilledCounter;
    private final Counter tradesExecutedCounter;
    private final Counter selfMatchSkippedCounter;

    public TradingMetricsService(MeterRegistry meterRegistry) {
        this.ordersCreatedCounter = Counter.builder("cex.orders.created")
                .description("Number of created orders")
                .register(meterRegistry);
        this.ordersCancelledCounter = Counter.builder("cex.orders.cancelled")
                .description("Number of cancelled orders")
                .register(meterRegistry);
        this.ordersFilledCounter = Counter.builder("cex.orders.filled")
                .description("Number of fully filled orders")
                .register(meterRegistry);
        this.ordersPartiallyFilledCounter = Counter.builder("cex.orders.partially_filled")
                .description("Number of partially filled orders")
                .register(meterRegistry);
        this.tradesExecutedCounter = Counter.builder("cex.trades.executed")
                .description("Number of executed trades")
                .register(meterRegistry);
        this.selfMatchSkippedCounter = Counter.builder("cex.matching.self_match_skipped")
                .description("Number of skipped self-match candidates")
                .register(meterRegistry);
    }

    public void incrementOrdersCreated() {
        ordersCreatedCounter.increment();
    }

    public void incrementOrdersCancelled() {
        ordersCancelledCounter.increment();
    }

    public void incrementOrdersFilled() {
        ordersFilledCounter.increment();
    }

    public void incrementOrdersPartiallyFilled() {
        ordersPartiallyFilledCounter.increment();
    }

    public void incrementTradesExecuted() {
        tradesExecutedCounter.increment();
    }

    public void incrementSelfMatchSkipped() {
        selfMatchSkippedCounter.increment();
    }
}
