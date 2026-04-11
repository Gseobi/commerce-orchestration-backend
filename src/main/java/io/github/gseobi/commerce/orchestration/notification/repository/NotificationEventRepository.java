package io.github.gseobi.commerce.orchestration.notification.repository;

import io.github.gseobi.commerce.orchestration.notification.entity.NotificationEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationEventRepository extends JpaRepository<NotificationEvent, Long> {

    List<NotificationEvent> findAllByOrderId(Long orderId);
}
