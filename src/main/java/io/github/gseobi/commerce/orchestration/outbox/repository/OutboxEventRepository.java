package io.github.gseobi.commerce.orchestration.outbox.repository;

import io.github.gseobi.commerce.orchestration.outbox.entity.OutboxEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findAllByOrderIdOrderByIdAsc(Long orderId);

    List<OutboxEvent> findTop100ByStatusOrderByIdAsc(io.github.gseobi.commerce.orchestration.outbox.entity.OutboxStatus status);
}
