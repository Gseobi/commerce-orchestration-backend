package io.github.gseobi.commerce.orchestration.settlement.api;

import java.util.List;

public interface SettlementApplication {

    Long request(Long orderId, String description);

    List<String> getSettlementStatuses(Long orderId);
}
