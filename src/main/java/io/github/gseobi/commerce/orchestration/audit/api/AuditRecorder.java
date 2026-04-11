package io.github.gseobi.commerce.orchestration.audit.api;

public interface AuditRecorder {

    void record(Long orderId, String action, String detail);
}
