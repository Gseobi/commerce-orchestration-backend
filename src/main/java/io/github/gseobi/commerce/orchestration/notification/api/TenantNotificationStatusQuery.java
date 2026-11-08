package io.github.gseobi.commerce.orchestration.notification.api;

import java.util.Optional;

/** Internal owner-scoped read. The caller must authorize access and verify the order first. */
public interface TenantNotificationStatusQuery {

    Optional<String> findLatestStatus(long merchantId, long orderId);
}
