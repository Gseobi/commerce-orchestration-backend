package io.github.gseobi.commerce.orchestration.notification.service;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationApplication;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationAdminApplication;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationAdminView;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationFailureView;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationRetryCandidateView;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationRetryOperations;
import io.github.gseobi.commerce.orchestration.notification.entity.NotificationEvent;
import io.github.gseobi.commerce.orchestration.notification.entity.NotificationEventStatus;
import io.github.gseobi.commerce.orchestration.notification.repository.NotificationEventRepository;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class NotificationService implements NotificationApplication, NotificationAdminApplication, NotificationRetryOperations {

    private static final String CHANNEL_ORDER_STATUS = "ORDER_STATUS";
    private static final String TOKEN_IGNORE = "FAIL_NOTIFICATION_IGNORE";
    private static final String TOKEN_RETRY = "FAIL_NOTIFICATION_RETRY";
    private static final String TOKEN_MANUAL = "FAIL_NOTIFICATION_MANUAL";
    private static final String TOKEN_FAIL = "FAIL_NOTIFICATION";

    private final NotificationEventRepository notificationEventRepository;

    @Transactional(noRollbackFor = BusinessException.class)
    @Override
    public Long request(Long orderId, String description) {
        NotificationEvent event = new NotificationEvent(
                orderId,
                NotificationEventStatus.REQUESTED,
                CHANNEL_ORDER_STATUS,
                "{\"message\":\"notification requested\"}"
        );

        if (description != null && description.contains(TOKEN_IGNORE)) {
            event.markIgnored("NOTIFICATION_IGNORE_ALLOWED", "현재 정책상 무시 가능한 알림 실패입니다.");
            return notificationEventRepository.save(event).getId();
        }

        if (description != null && description.contains(TOKEN_RETRY)) {
            event.scheduleRetry(
                    "NOTIFICATION_TRANSIENT_FAILURE",
                    "일시적 알림 실패로 자동 재시도 대상입니다.",
                    LocalDateTime.now().plusMinutes(5)
            );
            NotificationEvent failedEvent = notificationEventRepository.save(event);
            throw new BusinessException(
                    ErrorCode.NOTIFICATION_REQUEST_FAILED,
                    "알림 요청 retry 대상 실패가 발생했습니다. notificationEventId=" + failedEvent.getId()
            );
        }

        if (description != null && (description.contains(TOKEN_MANUAL) || description.contains(TOKEN_FAIL))) {
            event.requireManualIntervention(
                    "NOTIFICATION_MANUAL_INTERVENTION_REQUIRED",
                    "운영자 확인이 필요한 알림 실패입니다."
            );
            NotificationEvent failedEvent = notificationEventRepository.save(event);
            throw new BusinessException(
                    ErrorCode.NOTIFICATION_REQUEST_FAILED,
                    "알림 요청 manual intervention 대상 실패가 발생했습니다. notificationEventId=" + failedEvent.getId()
            );
        }
        return notificationEventRepository.save(event).getId();
    }

    @Override
    public List<String> getNotificationStatuses(Long orderId) {
        return notificationEventRepository.findAllByOrderIdOrderByIdAsc(orderId)
                .stream()
                .map(notificationEvent -> notificationEvent.getStatus().name())
                .toList();
    }

    @Override
    public Optional<NotificationFailureView> getLatestNotificationFailure(Long orderId) {
        return notificationEventRepository.findFirstByOrderIdOrderByIdDesc(orderId)
                .map(event -> new NotificationFailureView(
                        event.getId(),
                        event.getStatus().name(),
                        event.getHandlingPolicy().name(),
                        event.getRetryCount(),
                        event.getNextAttemptAt(),
                        event.getLastAttemptAt(),
                        event.getFailureCode(),
                        event.getFailureReason()
                ));
    }

    @Override
    public List<NotificationRetryCandidateView> findDueRetryScheduledEvents(LocalDateTime now, int limit) {
        return notificationEventRepository.findDueRetryScheduledEvents(
                        NotificationEventStatus.RETRY_SCHEDULED,
                        now,
                        3,
                        PageRequest.of(0, limit)
                ).stream()
                .map(event -> new NotificationRetryCandidateView(
                        event.getId(),
                        event.getOrderId(),
                        event.getRetryCount(),
                        event.getNextAttemptAt()
                ))
                .toList();
    }

    @Transactional
    @Override
    public NotificationAdminView markRetrySucceeded(Long notificationEventId, LocalDateTime attemptedAt) {
        NotificationEvent event = getNotificationEvent(notificationEventId);
        event.markSent(attemptedAt);
        return toAdminView(event);
    }

    @Transactional
    @Override
    public NotificationFailureView rescheduleRetry(
            Long notificationEventId,
            String failureCode,
            String failureReason,
            LocalDateTime attemptedAt,
            LocalDateTime nextAttemptAt
    ) {
        NotificationEvent event = getNotificationEvent(notificationEventId);
        event.scheduleRetry(failureCode, failureReason, attemptedAt, nextAttemptAt);
        return toFailureView(event);
    }

    @Transactional
    @Override
    public NotificationFailureView requireManualIntervention(
            Long notificationEventId,
            String failureCode,
            String failureReason,
            LocalDateTime attemptedAt
    ) {
        NotificationEvent event = getNotificationEvent(notificationEventId);
        event.requireManualIntervention(failureCode, failureReason, attemptedAt);
        return toFailureView(event);
    }

    @Transactional
    @Override
    public NotificationAdminView retryNotification(Long notificationEventId) {
        NotificationEvent event = getNotificationEvent(notificationEventId);
        if (!isRetryableByAdmin(event)) {
            throw new BusinessException(ErrorCode.ADMIN_REPROCESS_NOT_ALLOWED,
                    "재처리 가능한 알림 이벤트가 아닙니다. current=%s".formatted(event.getStatus()));
        }
        event.markSentByAdminRetry();
        return toAdminView(event);
    }

    @Transactional
    @Override
    public NotificationAdminView ignoreNotification(Long notificationEventId) {
        NotificationEvent event = getNotificationEvent(notificationEventId);
        if (!isIgnorableByAdmin(event)) {
            throw new BusinessException(ErrorCode.ADMIN_REPROCESS_NOT_ALLOWED,
                    "무시 처리 가능한 알림 이벤트가 아닙니다. current=%s".formatted(event.getStatus()));
        }
        event.markIgnoredByAdmin();
        return toAdminView(event);
    }

    private NotificationEvent getNotificationEvent(Long notificationEventId) {
        return notificationEventRepository.findById(notificationEventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_EVENT_NOT_FOUND));
    }

    private boolean isRetryableByAdmin(NotificationEvent event) {
        return event.getStatus() == NotificationEventStatus.RETRY_SCHEDULED
                || event.getStatus() == NotificationEventStatus.MANUAL_INTERVENTION_REQUIRED
                || event.getStatus() == NotificationEventStatus.FAILED;
    }

    private boolean isIgnorableByAdmin(NotificationEvent event) {
        return event.getStatus() == NotificationEventStatus.RETRY_SCHEDULED
                || event.getStatus() == NotificationEventStatus.MANUAL_INTERVENTION_REQUIRED
                || event.getStatus() == NotificationEventStatus.FAILED;
    }

    private NotificationAdminView toAdminView(NotificationEvent event) {
        return new NotificationAdminView(
                event.getId(),
                event.getOrderId(),
                event.getStatus().name(),
                event.getHandlingPolicy().name(),
                event.getRetryCount(),
                event.getNextAttemptAt(),
                event.getLastAttemptAt(),
                event.getFailureCode(),
                event.getFailureReason()
        );
    }

    private NotificationFailureView toFailureView(NotificationEvent event) {
        return new NotificationFailureView(
                event.getId(),
                event.getStatus().name(),
                event.getHandlingPolicy().name(),
                event.getRetryCount(),
                event.getNextAttemptAt(),
                event.getLastAttemptAt(),
                event.getFailureCode(),
                event.getFailureReason()
        );
    }
}
