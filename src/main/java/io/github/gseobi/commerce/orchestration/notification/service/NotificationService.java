package io.github.gseobi.commerce.orchestration.notification.service;

import io.github.gseobi.commerce.orchestration.notification.entity.NotificationEvent;
import io.github.gseobi.commerce.orchestration.notification.entity.NotificationEventStatus;
import io.github.gseobi.commerce.orchestration.notification.repository.NotificationEventRepository;
import io.github.gseobi.commerce.orchestration.order.entity.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationEventRepository notificationEventRepository;

    public NotificationService(NotificationEventRepository notificationEventRepository) {
        this.notificationEventRepository = notificationEventRepository;
    }

    @Transactional
    public NotificationEvent request(Order order) {
        NotificationEvent event = new NotificationEvent(
                order,
                NotificationEventStatus.REQUESTED,
                "ORDER_STATUS",
                "{\"message\":\"notification requested\"}"
        );
        return notificationEventRepository.save(event);
    }
}
