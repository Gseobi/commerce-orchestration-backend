package io.github.gseobi.commerce.orchestration.audit.repository;

import io.github.gseobi.commerce.orchestration.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findAllByOrderIdOrderByIdAsc(Long orderId);
}
