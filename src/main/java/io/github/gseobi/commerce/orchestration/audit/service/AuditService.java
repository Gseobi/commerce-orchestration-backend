package io.github.gseobi.commerce.orchestration.audit.service;

import io.github.gseobi.commerce.orchestration.audit.api.AuditRecorder;
import io.github.gseobi.commerce.orchestration.audit.entity.AuditLog;
import io.github.gseobi.commerce.orchestration.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class AuditService implements AuditRecorder {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    @Override
    public void record(Long orderId, String action, String detail) {
        auditLogRepository.save(new AuditLog(orderId, action, detail));
    }
}
