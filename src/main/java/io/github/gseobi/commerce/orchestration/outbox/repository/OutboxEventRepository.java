package io.github.gseobi.commerce.orchestration.outbox.repository;

import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxEvent;
import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
