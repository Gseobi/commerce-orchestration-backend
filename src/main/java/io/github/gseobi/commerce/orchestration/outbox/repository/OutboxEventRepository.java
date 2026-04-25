package io.github.gseobi.commerce.orchestration.outbox.repository;

import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxEvent;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findAllByOrderIdOrderByIdAsc(Long orderId);

    @Query("""
            select event
            from OutboxEvent event
            where event.status in :statuses
              and event.nextAttemptAt <= :now
            order by event.nextAttemptAt asc, event.id asc
            """)
    List<OutboxEvent> findPublishableEvents(
            @Param("statuses") List<OutboxStatus> statuses,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OutboxEvent event
            set event.status = io.github.gseobi.commerce.orchestration.outbox.entity.OutboxStatus.PROCESSING,
                event.lastAttemptAt = :now,
                event.version = event.version + 1
            where event.id = :id
              and event.status in :statuses
              and event.nextAttemptAt <= :now
            """)
    int claimPublishableEvent(
            @Param("id") Long id,
            @Param("statuses") List<OutboxStatus> statuses,
            @Param("now") LocalDateTime now
    );
}
