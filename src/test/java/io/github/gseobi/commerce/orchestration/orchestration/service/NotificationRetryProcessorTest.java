package io.github.gseobi.commerce.orchestration.orchestration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.gseobi.commerce.orchestration.audit.api.AuditRecorder;
import io.github.gseobi.commerce.orchestration.common.metrics.CommerceRecoveryMetrics;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationAdminView;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationFailureView;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationRetryCandidateView;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationRetryOperations;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationRetryProcessingResult;
import io.github.gseobi.commerce.orchestration.order.api.OrderExecutionView;
import io.github.gseobi.commerce.orchestration.order.api.OrderRecoveryApplication;
import io.github.gseobi.commerce.orchestration.order.api.OrderWorkflowAccess;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class NotificationRetryProcessorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 25, 12, 0);
    private static final NotificationRetryCandidateView RETRY_CANDIDATE =
            new NotificationRetryCandidateView(10L, 20L, 1, NOW.minusMinutes(1));
    private static final NotificationRetryCandidateView MANUAL_CANDIDATE =
            new NotificationRetryCandidateView(11L, 21L, 2, NOW.minusMinutes(1));

    @Test
    void processDueRetries_skipsStaleCandidateAlreadyClaimedAsProcessing() {
        NotificationRetryOperations retryOperations = new SingleCandidateRetryOperations(0);
        OrderWorkflowAccess orderWorkflowAccess = mock(OrderWorkflowAccess.class);
        OrderRecoveryApplication orderRecoveryApplication = mock(OrderRecoveryApplication.class);
        AuditRecorder auditRecorder = mock(AuditRecorder.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        NotificationRetryProcessor processor = new NotificationRetryProcessor(
                retryOperations,
                orderWorkflowAccess,
                orderRecoveryApplication,
                auditRecorder,
                new CommerceRecoveryMetrics(meterRegistry)
        );

        NotificationRetryProcessingResult result = processor.processDueRetries(NOW, 10);

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.successCount()).isZero();
        assertThat(result.failedCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.processedEventIds()).isEmpty();
        assertThat(counter(meterRegistry, "commerce.notification.retry.batch.started")).isEqualTo(1.0);
        assertThat(meterRegistry.find("commerce.notification.retry.skipped")
                .tag("reason", "claim_failed")
                .counter()
                .count())
                .isEqualTo(1.0);
        verify(orderWorkflowAccess, times(0)).getOrderExecutionView(RETRY_CANDIDATE.orderId());
        verify(orderRecoveryApplication, times(0)).completeAfterNotificationRecovery(RETRY_CANDIDATE.orderId());
    }

    @Test
    void processDueRetries_concurrentRunsProcessSameDueEventOnlyOnce() throws Exception {
        ClaimOnceRetryOperations retryOperations = new ClaimOnceRetryOperations(4);
        OrderWorkflowAccess orderWorkflowAccess = mock(OrderWorkflowAccess.class);
        when(orderWorkflowAccess.getOrderExecutionView(RETRY_CANDIDATE.orderId()))
                .thenReturn(new OrderExecutionView(
                        RETRY_CANDIDATE.orderId(),
                        "FAILED",
                        BigDecimal.valueOf(10000),
                        "transient notification retry"
                ));
        OrderRecoveryApplication orderRecoveryApplication = mock(OrderRecoveryApplication.class);
        AuditRecorder auditRecorder = mock(AuditRecorder.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        NotificationRetryProcessor processor = new NotificationRetryProcessor(
                retryOperations,
                orderWorkflowAccess,
                orderRecoveryApplication,
                auditRecorder,
                new CommerceRecoveryMetrics(meterRegistry)
        );

        ExecutorService executorService = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<NotificationRetryProcessingResult>> futures = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            futures.add(executorService.submit(() -> {
                retryOperations.ready();
                start.await(5, TimeUnit.SECONDS);
                return processor.processDueRetries(NOW, 10);
            }));
        }

        assertThat(retryOperations.awaitReady()).isTrue();
        start.countDown();

        List<NotificationRetryProcessingResult> results = new ArrayList<>();
        for (Future<NotificationRetryProcessingResult> future : futures) {
            results.add(future.get(5, TimeUnit.SECONDS));
        }
        executorService.shutdown();
        assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(results).extracting(NotificationRetryProcessingResult::processedCount).containsOnly(1);
        assertThat(results).extracting(NotificationRetryProcessingResult::failedCount).containsOnly(0);
        assertThat(results).extracting(NotificationRetryProcessingResult::successCount).containsExactlyInAnyOrder(1, 0, 0, 0);
        assertThat(results).extracting(NotificationRetryProcessingResult::skippedCount).containsExactlyInAnyOrder(0, 1, 1, 1);
        assertThat(results.stream().flatMap(result -> result.processedEventIds().stream()).toList())
                .containsExactly(RETRY_CANDIDATE.notificationEventId());
        assertThat(meterRegistry.find("commerce.notification.retry.success")
                .tag("failureCode", "none")
                .counter()
                .count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.find("commerce.notification.retry.skipped")
                .tag("reason", "claim_failed")
                .counter()
                .count())
                .isEqualTo(3.0);
        verify(orderRecoveryApplication, times(1)).completeAfterNotificationRecovery(RETRY_CANDIDATE.orderId());
        verify(auditRecorder, times(1)).record(
                RETRY_CANDIDATE.orderId(),
                "NOTIFICATION_RETRY_PROCESSED_SUCCESS",
                "notificationEventId=10, retryCount=2"
        );
    }

    @Test
    void processDueRetries_successIncrementsRetrySuccessMetric() {
        NotificationRetryOperations retryOperations = new CandidateRetryOperations(RETRY_CANDIDATE, 1);
        OrderWorkflowAccess orderWorkflowAccess = mock(OrderWorkflowAccess.class);
        when(orderWorkflowAccess.getOrderExecutionView(RETRY_CANDIDATE.orderId()))
                .thenReturn(new OrderExecutionView(
                        RETRY_CANDIDATE.orderId(),
                        "FAILED",
                        BigDecimal.valueOf(10000),
                        "transient notification retry"
                ));
        OrderRecoveryApplication orderRecoveryApplication = mock(OrderRecoveryApplication.class);
        AuditRecorder auditRecorder = mock(AuditRecorder.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        NotificationRetryProcessor processor = new NotificationRetryProcessor(
                retryOperations,
                orderWorkflowAccess,
                orderRecoveryApplication,
                auditRecorder,
                new CommerceRecoveryMetrics(meterRegistry)
        );

        NotificationRetryProcessingResult result = processor.processDueRetries(NOW, 10);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(meterRegistry.find("commerce.notification.retry.success")
                .tag("failureCode", "none")
                .counter()
                .count())
                .isEqualTo(1.0);
    }

    @Test
    void processDueRetries_manualInterventionIncrementsManualRequiredMetric() {
        NotificationRetryOperations retryOperations = new CandidateRetryOperations(MANUAL_CANDIDATE, 1);
        OrderWorkflowAccess orderWorkflowAccess = mock(OrderWorkflowAccess.class);
        when(orderWorkflowAccess.getOrderExecutionView(MANUAL_CANDIDATE.orderId()))
                .thenReturn(new OrderExecutionView(
                        MANUAL_CANDIDATE.orderId(),
                        "FAILED",
                        BigDecimal.valueOf(10000),
                        "FAIL_NOTIFICATION_RETRY_PERSISTENT"
                ));
        OrderRecoveryApplication orderRecoveryApplication = mock(OrderRecoveryApplication.class);
        AuditRecorder auditRecorder = mock(AuditRecorder.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        NotificationRetryProcessor processor = new NotificationRetryProcessor(
                retryOperations,
                orderWorkflowAccess,
                orderRecoveryApplication,
                auditRecorder,
                new CommerceRecoveryMetrics(meterRegistry)
        );

        NotificationRetryProcessingResult result = processor.processDueRetries(NOW, 10);

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(meterRegistry.find("commerce.notification.retry.manual_required")
                .tag("failureCode", "NOTIFICATION_RETRY_EXHAUSTED")
                .counter()
                .count())
                .isEqualTo(1.0);
    }

    private double counter(SimpleMeterRegistry meterRegistry, String name) {
        return meterRegistry.find(name)
                .counter()
                .count();
    }

    private static class SingleCandidateRetryOperations implements NotificationRetryOperations {

        private final int claimResult;

        SingleCandidateRetryOperations(int claimResult) {
            this.claimResult = claimResult;
        }

        @Override
        public List<NotificationRetryCandidateView> findDueRetryScheduledEvents(LocalDateTime now, int limit) {
            return List.of(RETRY_CANDIDATE);
        }

        @Override
        public int claimRetryScheduledEvent(Long notificationEventId, LocalDateTime now, int maxRetryCount) {
            return claimResult;
        }

        @Override
        public NotificationAdminView markRetrySucceeded(Long notificationEventId, LocalDateTime attemptedAt) {
            return null;
        }

        @Override
        public NotificationFailureView rescheduleRetry(
                Long notificationEventId,
                String failureCode,
                String failureReason,
                LocalDateTime attemptedAt,
                LocalDateTime nextAttemptAt
        ) {
            return null;
        }

        @Override
        public NotificationFailureView requireManualIntervention(
                Long notificationEventId,
                String failureCode,
                String failureReason,
                LocalDateTime attemptedAt
        ) {
            return null;
        }
    }

    private static class ClaimOnceRetryOperations extends SingleCandidateRetryOperations {

        private final AtomicBoolean claimed = new AtomicBoolean(false);
        private final CountDownLatch readyLatch;

        ClaimOnceRetryOperations(int runnerCount) {
            super(0);
            this.readyLatch = new CountDownLatch(runnerCount);
        }

        void ready() {
            readyLatch.countDown();
        }

        boolean awaitReady() throws InterruptedException {
            return readyLatch.await(5, TimeUnit.SECONDS);
        }

        @Override
        public int claimRetryScheduledEvent(Long notificationEventId, LocalDateTime now, int maxRetryCount) {
            return claimed.compareAndSet(false, true) ? 1 : 0;
        }
    }

    private static class CandidateRetryOperations implements NotificationRetryOperations {

        private final NotificationRetryCandidateView candidate;
        private final int claimResult;

        CandidateRetryOperations(NotificationRetryCandidateView candidate, int claimResult) {
            this.candidate = candidate;
            this.claimResult = claimResult;
        }

        @Override
        public List<NotificationRetryCandidateView> findDueRetryScheduledEvents(LocalDateTime now, int limit) {
            return List.of(candidate);
        }

        @Override
        public int claimRetryScheduledEvent(Long notificationEventId, LocalDateTime now, int maxRetryCount) {
            return claimResult;
        }

        @Override
        public NotificationAdminView markRetrySucceeded(Long notificationEventId, LocalDateTime attemptedAt) {
            return null;
        }

        @Override
        public NotificationFailureView rescheduleRetry(
                Long notificationEventId,
                String failureCode,
                String failureReason,
                LocalDateTime attemptedAt,
                LocalDateTime nextAttemptAt
        ) {
            return null;
        }

        @Override
        public NotificationFailureView requireManualIntervention(
                Long notificationEventId,
                String failureCode,
                String failureReason,
                LocalDateTime attemptedAt
        ) {
            return null;
        }
    }
}
