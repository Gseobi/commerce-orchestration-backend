package io.github.gseobi.commerce.orchestration.settlement.repository;

import io.github.gseobi.commerce.orchestration.settlement.entity.Settlement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findAllByOrderId(Long orderId);

    List<Settlement> findAllByOrderIdOrderByIdAsc(Long orderId);
}
