package io.github.gseobi.commerce.orchestration.notification.api;

import java.util.*;

public interface NotificationApplication {

    Long request(Long orderId, String description);

    List<String> getNotificationStatuses(Long orderId);

    Optional<NotificationFailureView> getLatestNotificationFailure(Long orderId);
}
