package org.william.cex;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.william.cex.api.dto.response.TradeReconciliationRunResponse;
import org.william.cex.domain.reconciliation.service.TradeReconciliationScheduler;
import org.william.cex.domain.reconciliation.service.TradeReconciliationService;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeReconciliationSchedulerTest {

    @Mock
    private TradeReconciliationService tradeReconciliationService;

    @Captor
    private ArgumentCaptor<LocalDateTime> fromCaptor;

    @Captor
    private ArgumentCaptor<LocalDateTime> toCaptor;

    @Test
    void schedulerTriggersAutoRepairWhenIssuesExist() {
        TradeReconciliationScheduler scheduler =
                new TradeReconciliationScheduler(tradeReconciliationService, 24, true);
        when(tradeReconciliationService.runTradeReconciliation(any(), any()))
                .thenReturn(TradeReconciliationRunResponse.builder()
                        .id(7L)
                        .totalIssues(2)
                        .build());

        scheduler.runScheduledTradeReconciliation();

        verify(tradeReconciliationService, times(1)).runTradeReconciliation(any(), any());
        verify(tradeReconciliationService, times(1)).repairRun(7L);
    }

    @Test
    void schedulerDoesNotAutoRepairWhenDisabled() {
        TradeReconciliationScheduler scheduler =
                new TradeReconciliationScheduler(tradeReconciliationService, 24, false);
        when(tradeReconciliationService.runTradeReconciliation(any(), any()))
                .thenReturn(TradeReconciliationRunResponse.builder()
                        .id(11L)
                        .totalIssues(4)
                        .build());

        scheduler.runScheduledTradeReconciliation();

        verify(tradeReconciliationService, times(1)).runTradeReconciliation(any(), any());
        verify(tradeReconciliationService, never()).repairRun(anyLong());
    }

    @Test
    void schedulerEnforcesMinimumLookbackWindowOfOneHour() {
        TradeReconciliationScheduler scheduler =
                new TradeReconciliationScheduler(tradeReconciliationService, 0, false);
        when(tradeReconciliationService.runTradeReconciliation(any(), any()))
                .thenReturn(TradeReconciliationRunResponse.builder()
                        .id(9L)
                        .totalIssues(0)
                        .build());

        scheduler.runScheduledTradeReconciliation();

        verify(tradeReconciliationService).runTradeReconciliation(fromCaptor.capture(), toCaptor.capture());
        long hoursBetween = Duration.between(fromCaptor.getValue(), toCaptor.getValue()).toHours();
        assertEquals(1L, hoursBetween);
    }
}
