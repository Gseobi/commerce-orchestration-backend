package io.github.gseobi.commerce.orchestration.settlement.service;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.settlement.api.SettlementApplication;
import io.github.gseobi.commerce.orchestration.settlement.entity.Settlement;
import io.github.gseobi.commerce.orchestration.settlement.entity.SettlementStatus;
import io.github.gseobi.commerce.orchestration.settlement.repository.SettlementRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class SettlementService implements SettlementApplication {

    private final SettlementRepository settlementRepository;

    @Transactional(noRollbackFor = BusinessException.class)
    @Override
    public Long request(Long orderId, String description) {
        if (description != null && description.contains("FAIL_SETTLEMENT")) {
            Settlement failedSettlement = settlementRepository.save(
                    new Settlement(orderId, SettlementStatus.FAILED, "Simulated settlement failure")
            );
            throw new BusinessException(
                    ErrorCode.SETTLEMENT_REQUEST_FAILED,
                    "정산 요청 mock 실패가 발생했습니다. settlementId=" + failedSettlement.getId()
            );
        }
        return settlementRepository.save(
                new Settlement(orderId, SettlementStatus.REQUESTED, "Initial settlement request")
        ).getId();
    }

    @Override
    public List<String> getSettlementStatuses(Long orderId) {
        return settlementRepository.findAllByOrderId(orderId)
                .stream()
                .map(settlement -> settlement.getStatus().name())
                .toList();
    }
}
