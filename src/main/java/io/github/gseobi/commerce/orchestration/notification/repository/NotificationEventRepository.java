package io.github.gseobi.commerce.orchestration.notification.repository;

import io.github.gseobi.commerce.orchestration.notification.entity.NotificationEvent;
import io.github.gseobi.commerce.orchestration.notification.entity.NotificationEventStatus;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationEvent event
            set event.status = io.github.gseobi.commerce.orchestration.notification.entity.NotificationEventStatus.PROCESSING,
                event.lastAttemptAt = :now,
                event.version = event.version + 1
            where event.id = :id
              and event.status = io.github.gseobi.commerce.orchestration.notification.entity.NotificationEventStatus.RETRY_SCHEDULED
              and event.nextAttemptAt <= :now
              and event.retryCount < :maxRetryCount
            """)
    int claimRetryScheduledEvent(
            @Param("id") Long id,
            @Param("now") LocalDateTime now,
            @Param("maxRetryCount") int maxRetryCount
    );
}
