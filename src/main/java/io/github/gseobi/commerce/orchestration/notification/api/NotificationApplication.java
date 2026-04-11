package io.github.gseobi.commerce.orchestration.notification.api;

import java.util.List;

public interface NotificationApplication {

    Long request(Long orderId, String description);

    List<String> getNotificationStatuses(Long orderId);
}
