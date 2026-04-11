package io.github.gseobi.commerce.orchestration.orchestration.repository;

import io.github.gseobi.commerce.orchestration.orchestration.entity.OrchestrationStep;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrchestrationStepRepository extends JpaRepository<OrchestrationStep, Long> {

    List<OrchestrationStep> findAllByOrderIdOrderByIdAsc(Long orderId);
}
