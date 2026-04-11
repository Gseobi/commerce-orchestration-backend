package io.github.gseobi.commerce.orchestration.settlement.service;

import io.github.gseobi.commerce.orchestration.order.entity.Order;
import io.github.gseobi.commerce.orchestration.settlement.entity.Settlement;
import io.github.gseobi.commerce.orchestration.settlement.entity.SettlementStatus;
import io.github.gseobi.commerce.orchestration.settlement.repository.SettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SettlementService {

    private final SettlementRepository settlementRepository;

    public SettlementService(SettlementRepository settlementRepository) {
        this.settlementRepository = settlementRepository;
    }

    @Transactional
    public Settlement request(Order order) {
        Settlement settlement = new Settlement(order, SettlementStatus.REQUESTED, "Initial settlement request");
        return settlementRepository.save(settlement);
    }
}
