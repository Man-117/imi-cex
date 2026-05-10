package org.william.cex.domain.reconciliation.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.william.cex.api.dto.response.TradeReconciliationRunResponse;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
@ConditionalOnProperty(
        prefix = "cex.reconciliation.auto",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TradeReconciliationScheduler {

    private final TradeReconciliationService tradeReconciliationService;
    private final int lookbackHours;
    private final boolean autoRepair;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public TradeReconciliationScheduler(
            TradeReconciliationService tradeReconciliationService,
            @Value("${cex.reconciliation.auto.lookback-hours:24}") int lookbackHours,
            @Value("${cex.reconciliation.auto.auto-repair:true}") boolean autoRepair) {
        this.tradeReconciliationService = tradeReconciliationService;
        this.lookbackHours = lookbackHours;
        this.autoRepair = autoRepair;
    }

    @Scheduled(
            cron = "${cex.reconciliation.auto.cron:0 */15 * * * *}",
            zone = "${cex.reconciliation.auto.zone:UTC}"
    )
    public void runScheduledTradeReconciliation() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Skipping scheduled trade reconciliation because previous run is still active");
            return;
        }

        try {
            int effectiveLookbackHours = Math.max(1, lookbackHours);
            LocalDateTime scopeTo = LocalDateTime.now();
            LocalDateTime scopeFrom = scopeTo.minusHours(effectiveLookbackHours);

            TradeReconciliationRunResponse run =
                    tradeReconciliationService.runTradeReconciliation(scopeFrom, scopeTo);

            Integer totalIssues = run.getTotalIssues();
            if (autoRepair && totalIssues != null && totalIssues > 0 && run.getId() != null) {
                tradeReconciliationService.repairRun(run.getId());
                log.info("Scheduled trade reconciliation run {} auto-repair completed", run.getId());
            } else {
                log.info("Scheduled trade reconciliation run {} completed without auto-repair", run.getId());
            }
        } catch (Exception ex) {
            log.error("Scheduled trade reconciliation failed", ex);
        } finally {
            running.set(false);
        }
    }
}
