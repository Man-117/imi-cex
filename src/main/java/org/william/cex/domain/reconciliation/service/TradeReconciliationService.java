package org.william.cex.domain.reconciliation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.william.cex.api.dto.response.TradeReconciliationIssueResponse;
import org.william.cex.api.dto.response.TradeReconciliationRunResponse;
import org.william.cex.api.exception.ReconciliationNotFoundException;
import org.william.cex.domain.fee.entity.FeeTransaction;
import org.william.cex.domain.fee.repository.FeeTransactionRepository;
import org.william.cex.domain.fee.service.FeeService;
import org.william.cex.domain.order.entity.Order;
import org.william.cex.domain.order.entity.Trade;
import org.william.cex.domain.order.repository.OrderRepository;
import org.william.cex.domain.order.repository.TradeRepository;
import org.william.cex.domain.reconciliation.entity.TradeReconciliationIssue;
import org.william.cex.domain.reconciliation.entity.TradeReconciliationRun;
import org.william.cex.domain.reconciliation.repository.TradeReconciliationIssueRepository;
import org.william.cex.domain.reconciliation.repository.TradeReconciliationRunRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TradeReconciliationService {

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private FeeTransactionRepository feeTransactionRepository;

    @Autowired
    private FeeService feeService;

    @Autowired
    private TradeReconciliationRunRepository runRepository;

    @Autowired
    private TradeReconciliationIssueRepository issueRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional
    public TradeReconciliationRunResponse runTradeReconciliation(LocalDateTime scopeFrom, LocalDateTime scopeTo) {
        LocalDateTime now = LocalDateTime.now();
        TradeReconciliationRun run = TradeReconciliationRun.builder()
                .reconciliationType(TradeReconciliationRun.ReconciliationType.TRADE_SETTLEMENT)
                .status(TradeReconciliationRun.RunStatus.RUNNING)
                .scopeFrom(scopeFrom)
                .scopeTo(scopeTo)
                .startedAt(now)
                .build();
        run = runRepository.save(run);

        try {
            List<Trade> trades = tradeRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(scopeFrom, scopeTo);
            Set<Long> orderIds = collectOrderIds(trades);
            Map<Long, Order> orderById = orderRepository.findAllById(orderIds)
                    .stream()
                    .collect(Collectors.toMap(Order::getId, order -> order));

            List<TradeReconciliationIssue> issues = new ArrayList<>();
            Map<Long, BigDecimal> expectedFilledByOrder = new HashMap<>();
            Map<Long, BigDecimal> expectedFeesByOrder = new HashMap<>();

            for (Trade trade : trades) {
                evaluateTrade(trade, run.getId(), orderById, issues, expectedFilledByOrder, expectedFeesByOrder);
            }

            compareOrderFilledAmounts(run.getId(), orderById, expectedFilledByOrder, issues);
            compareFeeLedger(run.getId(), expectedFeesByOrder, issues);

            if (!issues.isEmpty()) {
                issueRepository.saveAll(issues);
            }

            run.setTotalTradesScanned(trades.size());
            run.setTotalIssues(issues.size());
            run.setAutoRepairedIssues(0);
            run.setStatus(issues.isEmpty()
                    ? TradeReconciliationRun.RunStatus.COMPLETED
                    : TradeReconciliationRun.RunStatus.COMPLETED_WITH_BREAKS);
            run.setSummary(objectMapper.valueToTree(buildSummary(issues)));
            run.setCompletedAt(LocalDateTime.now());
            run = runRepository.save(run);

            log.info("Trade reconciliation run {} completed: trades={} issues={}",
                    run.getId(), run.getTotalTradesScanned(), run.getTotalIssues());
            return toRunResponse(run, issueRepository.findByRunIdOrderByIdAsc(run.getId()));
        } catch (Exception ex) {
            run.setStatus(TradeReconciliationRun.RunStatus.FAILED);
            run.setFailureReason(ex.getMessage());
            run.setCompletedAt(LocalDateTime.now());
            runRepository.save(run);
            log.error("Trade reconciliation run {} failed", run.getId(), ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<TradeReconciliationRunResponse> getRecentRuns() {
        return runRepository.findTop20ByOrderByStartedAtDesc()
                .stream()
                .map(run -> toRunResponse(run, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public TradeReconciliationRunResponse getRunDetails(Long runId) {
        TradeReconciliationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ReconciliationNotFoundException("Reconciliation run not found: " + runId));
        List<TradeReconciliationIssue> issues = issueRepository.findByRunIdOrderByIdAsc(runId);
        return toRunResponse(run, issues);
    }

    @Transactional
    public TradeReconciliationRunResponse repairRun(Long runId) {
        TradeReconciliationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ReconciliationNotFoundException("Reconciliation run not found: " + runId));
        List<TradeReconciliationIssue> openIssues = issueRepository.findByRunIdAndStatus(runId, TradeReconciliationIssue.IssueStatus.OPEN);
        if (openIssues.isEmpty()) {
            return toRunResponse(run, issueRepository.findByRunIdOrderByIdAsc(runId));
        }

        int autoRepaired = 0;
        for (TradeReconciliationIssue issue : openIssues) {
            boolean repaired = tryAutoRepair(issue);
            if (repaired) {
                autoRepaired++;
                issue.setStatus(TradeReconciliationIssue.IssueStatus.AUTO_REPAIRED);
                issue.setResolvedAt(LocalDateTime.now());
            } else {
                issue.setStatus(TradeReconciliationIssue.IssueStatus.MANUAL_REVIEW);
            }
        }
        issueRepository.saveAll(openIssues);

        run.setAutoRepairedIssues(run.getAutoRepairedIssues() + autoRepaired);
        boolean unresolvedExists = issueRepository.findByRunIdAndStatus(runId, TradeReconciliationIssue.IssueStatus.OPEN).size() > 0;
        boolean manualReviewExists = issueRepository.findByRunIdAndStatus(runId, TradeReconciliationIssue.IssueStatus.MANUAL_REVIEW).size() > 0;
        run.setStatus(!unresolvedExists && !manualReviewExists
                ? TradeReconciliationRun.RunStatus.REPAIRED
                : TradeReconciliationRun.RunStatus.COMPLETED_WITH_BREAKS);
        run.setCompletedAt(LocalDateTime.now());
        run = runRepository.save(run);

        log.info("Trade reconciliation run {} repaired. autoRepaired={}", runId, autoRepaired);
        return toRunResponse(run, issueRepository.findByRunIdOrderByIdAsc(runId));
    }

    private void evaluateTrade(Trade trade,
                               Long runId,
                               Map<Long, Order> orderById,
                               List<TradeReconciliationIssue> issues,
                               Map<Long, BigDecimal> expectedFilledByOrder,
                               Map<Long, BigDecimal> expectedFeesByOrder) {
        if (!"SETTLED".equalsIgnoreCase(trade.getSettlementStatus()) || trade.getSettledAt() == null) {
            issues.add(buildIssue(
                    runId,
                    TradeReconciliationIssue.IssueType.UNSETTLED_TRADE,
                    TradeReconciliationIssue.Severity.HIGH,
                    TradeReconciliationIssue.ReferenceType.TRADE,
                    trade.getId(),
                    "Trade is not marked as settled",
                    null,
                    null,
                    Map.of("settlementStatus", String.valueOf(trade.getSettlementStatus()))
            ));
        }

        Order buyOrder = orderById.get(trade.getBuyOrderId());
        Order sellOrder = orderById.get(trade.getSellOrderId());
        if (buyOrder == null || sellOrder == null) {
            issues.add(buildIssue(
                    runId,
                    TradeReconciliationIssue.IssueType.MISSING_ORDER,
                    TradeReconciliationIssue.Severity.HIGH,
                    TradeReconciliationIssue.ReferenceType.TRADE,
                    trade.getId(),
                    "Trade references missing order records",
                    null,
                    null,
                    Map.of("buyOrderId", trade.getBuyOrderId(), "sellOrderId", trade.getSellOrderId())
            ));
            return;
        }

        if (buyOrder.getOrderType() != Order.OrderType.BUY || sellOrder.getOrderType() != Order.OrderType.SELL) {
            issues.add(buildIssue(
                    runId,
                    TradeReconciliationIssue.IssueType.ORDER_SIDE_MISMATCH,
                    TradeReconciliationIssue.Severity.HIGH,
                    TradeReconciliationIssue.ReferenceType.TRADE,
                    trade.getId(),
                    "Trade linked orders have inconsistent side assignments",
                    null,
                    null,
                    Map.of("buyOrderType", buyOrder.getOrderType(), "sellOrderType", sellOrder.getOrderType())
            ));
        }

        boolean pairMismatch = !Objects.equals(buyOrder.getBaseCurrency(), sellOrder.getBaseCurrency())
                || !Objects.equals(buyOrder.getQuoteCurrency(), sellOrder.getQuoteCurrency());
        if (pairMismatch) {
            issues.add(buildIssue(
                    runId,
                    TradeReconciliationIssue.IssueType.ORDER_PAIR_MISMATCH,
                    TradeReconciliationIssue.Severity.MEDIUM,
                    TradeReconciliationIssue.ReferenceType.TRADE,
                    trade.getId(),
                    "Trade linked orders have mismatched currency pairs",
                    null,
                    null,
                    Map.of(
                            "buyPair", buyOrder.getBaseCurrency() + "/" + buyOrder.getQuoteCurrency(),
                            "sellPair", sellOrder.getBaseCurrency() + "/" + sellOrder.getQuoteCurrency()
                    )
            ));
        }

        BigDecimal normalizedAmount = scale(trade.getAmount());
        expectedFilledByOrder.merge(buyOrder.getId(), normalizedAmount, this::sumScaled);
        expectedFilledByOrder.merge(sellOrder.getId(), normalizedAmount, this::sumScaled);

        String pair = buyOrder.getBaseCurrency() + "/" + buyOrder.getQuoteCurrency();
        BigDecimal tradeNotional = scale(trade.getAmount().multiply(trade.getPrice()));
        BigDecimal expectedFee = feeService.calculateTradingFee(pair, tradeNotional);
        expectedFeesByOrder.merge(buyOrder.getId(), expectedFee, this::sumScaled);
        expectedFeesByOrder.merge(sellOrder.getId(), expectedFee, this::sumScaled);
    }

    private void compareOrderFilledAmounts(Long runId,
                                           Map<Long, Order> orderById,
                                           Map<Long, BigDecimal> expectedFilledByOrder,
                                           List<TradeReconciliationIssue> issues) {
        for (Map.Entry<Long, BigDecimal> entry : expectedFilledByOrder.entrySet()) {
            Order order = orderById.get(entry.getKey());
            if (order == null) {
                continue;
            }

            BigDecimal expected = scale(entry.getValue());
            BigDecimal actual = scale(order.getFilledAmount());
            if (actual.compareTo(expected) != 0) {
                issues.add(buildIssue(
                        runId,
                        TradeReconciliationIssue.IssueType.ORDER_FILLED_MISMATCH,
                        TradeReconciliationIssue.Severity.HIGH,
                        TradeReconciliationIssue.ReferenceType.ORDER,
                        order.getId(),
                        "Order filled amount does not match sum of reconciled trades",
                        expected,
                        actual,
                        Map.of("orderStatus", order.getStatus())
                ));
            }
        }
    }

    private void compareFeeLedger(Long runId,
                                  Map<Long, BigDecimal> expectedFeesByOrder,
                                  List<TradeReconciliationIssue> issues) {
        if (expectedFeesByOrder.isEmpty()) {
            return;
        }

        Map<Long, BigDecimal> actualFeesByOrder = new HashMap<>();
        List<Object[]> feeRows = feeTransactionRepository.sumFeesByOrderIdsAndType(
                expectedFeesByOrder.keySet(),
                FeeTransaction.FeeType.TRADING_FEE
        );
        for (Object[] row : feeRows) {
            Long orderId = (Long) row[0];
            BigDecimal total = row[1] == null ? BigDecimal.ZERO : scale((BigDecimal) row[1]);
            actualFeesByOrder.put(orderId, total);
        }

        for (Map.Entry<Long, BigDecimal> expectedEntry : expectedFeesByOrder.entrySet()) {
            Long orderId = expectedEntry.getKey();
            BigDecimal expected = scale(expectedEntry.getValue());
            BigDecimal actual = actualFeesByOrder.getOrDefault(orderId, BigDecimal.ZERO).setScale(8, RoundingMode.HALF_UP);
            if (actual.compareTo(expected) != 0) {
                issues.add(buildIssue(
                        runId,
                        TradeReconciliationIssue.IssueType.FEE_LEDGER_MISMATCH,
                        TradeReconciliationIssue.Severity.MEDIUM,
                        TradeReconciliationIssue.ReferenceType.ORDER,
                        orderId,
                        "Trading fee ledger mismatch for order",
                        expected,
                        actual,
                        null
                ));
            }
        }
    }

    private boolean tryAutoRepair(TradeReconciliationIssue issue) {
        switch (issue.getIssueType()) {
            case UNSETTLED_TRADE:
                return tradeRepository.findById(issue.getReferenceId())
                        .map(trade -> {
                            trade.setSettlementStatus("SETTLED");
                            if (trade.getSettledAt() == null) {
                                trade.setSettledAt(trade.getCreatedAt());
                            }
                            tradeRepository.save(trade);
                            return true;
                        })
                        .orElse(false);
            case ORDER_FILLED_MISMATCH:
                return orderRepository.findById(issue.getReferenceId())
                        .map(order -> {
                            if (issue.getExpectedValue() == null) {
                                return false;
                            }
                            BigDecimal expectedFilled = scale(issue.getExpectedValue());
                            if (expectedFilled.compareTo(order.getAmount()) > 0) {
                                return false;
                            }
                            order.setFilledAmount(expectedFilled);
                            if (order.getStatus() != Order.OrderStatus.CANCELLED) {
                                if (expectedFilled.compareTo(order.getAmount()) == 0) {
                                    order.setStatus(Order.OrderStatus.FILLED);
                                } else if (expectedFilled.compareTo(BigDecimal.ZERO) > 0) {
                                    order.setStatus(Order.OrderStatus.PARTIALLY_FILLED);
                                } else {
                                    order.setStatus(Order.OrderStatus.PENDING);
                                }
                            }
                            orderRepository.save(order);
                            return true;
                        })
                        .orElse(false);
            case FEE_LEDGER_MISMATCH:
                if (issue.getExpectedValue() == null || issue.getActualValue() == null) {
                    return false;
                }
                BigDecimal delta = scale(issue.getExpectedValue().subtract(issue.getActualValue()));
                if (delta.compareTo(BigDecimal.ZERO) <= 0) {
                    return false;
                }
                FeeTransaction transaction = FeeTransaction.builder()
                        .orderId(issue.getReferenceId())
                        .amount(delta)
                        .feeType(FeeTransaction.FeeType.TRADING_FEE)
                        .build();
                feeTransactionRepository.save(transaction);
                return true;
            default:
                return false;
        }
    }

    private TradeReconciliationIssue buildIssue(Long runId,
                                                TradeReconciliationIssue.IssueType issueType,
                                                TradeReconciliationIssue.Severity severity,
                                                TradeReconciliationIssue.ReferenceType referenceType,
                                                Long referenceId,
                                                String message,
                                                BigDecimal expectedValue,
                                                BigDecimal actualValue,
                                                Map<String, Object> metadata) {
        return TradeReconciliationIssue.builder()
                .runId(runId)
                .issueType(issueType)
                .severity(severity)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .message(message)
                .expectedValue(expectedValue == null ? null : scale(expectedValue))
                .actualValue(actualValue == null ? null : scale(actualValue))
                .metadata(metadata == null ? null : objectMapper.valueToTree(metadata))
                .status(TradeReconciliationIssue.IssueStatus.OPEN)
                .build();
    }

    private Set<Long> collectOrderIds(List<Trade> trades) {
        Set<Long> orderIds = new HashSet<>();
        for (Trade trade : trades) {
            orderIds.add(trade.getBuyOrderId());
            orderIds.add(trade.getSellOrderId());
        }
        return orderIds;
    }

    private Map<String, Object> buildSummary(List<TradeReconciliationIssue> issues) {
        Map<String, Long> issueCountByType = issues.stream()
                .collect(Collectors.groupingBy(issue -> issue.getIssueType().name(), Collectors.counting()));
        Map<String, Object> summary = new HashMap<>();
        summary.put("issueCountByType", issueCountByType);
        summary.put("highSeverity", issues.stream().filter(i -> i.getSeverity() == TradeReconciliationIssue.Severity.HIGH).count());
        summary.put("mediumSeverity", issues.stream().filter(i -> i.getSeverity() == TradeReconciliationIssue.Severity.MEDIUM).count());
        summary.put("lowSeverity", issues.stream().filter(i -> i.getSeverity() == TradeReconciliationIssue.Severity.LOW).count());
        return summary;
    }

    private TradeReconciliationRunResponse toRunResponse(TradeReconciliationRun run, List<TradeReconciliationIssue> issues) {
        return TradeReconciliationRunResponse.builder()
                .id(run.getId())
                .reconciliationType(run.getReconciliationType().name())
                .status(run.getStatus().name())
                .scopeFrom(run.getScopeFrom())
                .scopeTo(run.getScopeTo())
                .totalTradesScanned(run.getTotalTradesScanned())
                .totalIssues(run.getTotalIssues())
                .autoRepairedIssues(run.getAutoRepairedIssues())
                .failureReason(run.getFailureReason())
                .summary(run.getSummary())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .issues(issues == null ? null : issues.stream().map(this::toIssueResponse).toList())
                .build();
    }

    private TradeReconciliationIssueResponse toIssueResponse(TradeReconciliationIssue issue) {
        return TradeReconciliationIssueResponse.builder()
                .id(issue.getId())
                .issueType(issue.getIssueType().name())
                .severity(issue.getSeverity().name())
                .referenceType(issue.getReferenceType().name())
                .referenceId(issue.getReferenceId())
                .message(issue.getMessage())
                .expectedValue(issue.getExpectedValue())
                .actualValue(issue.getActualValue())
                .status(issue.getStatus().name())
                .createdAt(issue.getCreatedAt())
                .resolvedAt(issue.getResolvedAt())
                .build();
    }

    private BigDecimal sumScaled(BigDecimal left, BigDecimal right) {
        return scale(left.add(right));
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(8, RoundingMode.HALF_UP);
    }
}
