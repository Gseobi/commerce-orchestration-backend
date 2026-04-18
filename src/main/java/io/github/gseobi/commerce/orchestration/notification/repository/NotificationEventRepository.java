package io.github.gseobi.commerce.orchestration.notification.repository;

import io.github.gseobi.commerce.orchestration.notification.entity.NotificationEvent;
import io.github.gseobi.commerce.orchestration.notification.entity.NotificationEventStatus;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationEventRepository extends JpaRepository<NotificationEvent, Long> {

    List<NotificationEvent> findAllByOrderId(Long orderId);

    List<NotificationEvent> findAllByOrderIdOrderByIdAsc(Long orderId);

    Optional<NotificationEvent> findFirstByOrderIdOrderByIdDesc(Long orderId);

    @Query("""
            select event
            from NotificationEvent event
            where event.status = :status
              and event.nextAttemptAt is not null
              and event.nextAttemptAt <= :now
              and event.retryCount < :maxRetryCount
            order by event.nextAttemptAt asc, event.id asc
            """)
    List<NotificationEvent> findDueRetryScheduledEvents(
            @Param("status") NotificationEventStatus status,
            @Param("now") LocalDateTime now,
            @Param("maxRetryCount") int maxRetryCount,
            Pageable pageable
    );
}
