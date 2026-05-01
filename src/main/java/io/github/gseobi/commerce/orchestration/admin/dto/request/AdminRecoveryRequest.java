package io.github.gseobi.commerce.orchestration.admin.dto.request;

import io.github.gseobi.commerce.orchestration.admin.api.AdminRecoveryContext;

public record AdminRecoveryRequest(
        String operatorId,
        String reason
) {

    public AdminRecoveryContext toContext() {
        return AdminRecoveryContext.of(operatorId, reason);
    }
}
