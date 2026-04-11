package io.github.gseobi.commerce.orchestration.notification.service;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.notification.api.NotificationApplication;
import io.github.gseobi.commerce.orchestration.notification.entity.NotificationEvent;
import io.github.gseobi.commerce.orchestration.notification.entity.NotificationEventStatus;
import io.github.gseobi.commerce.orchestration.notification.repository.NotificationEventRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class NotificationService implements NotificationApplication {

    private final NotificationEventRepository notificationEventRepository;

    @Transactional(noRollbackFor = BusinessException.class)
    @Override
    public Long request(Long orderId, String description) {
        if (description != null && description.contains("FAIL_NOTIFICATION")) {
            NotificationEvent failedEvent = notificationEventRepository.save(
                    new NotificationEvent(orderId, NotificationEventStatus.FAILED, "ORDER_STATUS", "notification failed")
            );
            throw new BusinessException(
                    ErrorCode.NOTIFICATION_REQUEST_FAILED,
                    "알림 요청 mock 실패가 발생했습니다. notificationEventId=" + failedEvent.getId()
            );
        }
        NotificationEvent event = new NotificationEvent(
                orderId,
                NotificationEventStatus.REQUESTED,
                "ORDER_STATUS",
                "{\"message\":\"notification requested\"}"
        );
        return notificationEventRepository.save(event).getId();
    }

    @Override
    public List<String> getNotificationStatuses(Long orderId) {
        return notificationEventRepository.findAllByOrderId(orderId)
                .stream()
                .map(notificationEvent -> notificationEvent.getStatus().name())
                .toList();
    }
}
