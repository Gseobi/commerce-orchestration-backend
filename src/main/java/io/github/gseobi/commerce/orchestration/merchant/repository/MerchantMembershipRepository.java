package io.github.gseobi.commerce.orchestration.merchant.repository;

import io.github.gseobi.commerce.orchestration.merchant.entity.MerchantMembership;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantMembershipRepository extends JpaRepository<MerchantMembership, Long> {

    Optional<MerchantMembership> findByMerchantIdAndActorIdAndActiveTrue(Long merchantId, String actorId);
}
