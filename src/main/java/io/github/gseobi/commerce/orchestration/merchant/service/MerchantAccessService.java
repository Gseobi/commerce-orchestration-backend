package io.github.gseobi.commerce.orchestration.merchant.service;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantAccessApplication;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantAccessContext;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantPermission;
import io.github.gseobi.commerce.orchestration.merchant.entity.MerchantMembership;
import io.github.gseobi.commerce.orchestration.merchant.repository.MerchantMembershipRepository;
import io.github.gseobi.commerce.orchestration.merchant.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class MerchantAccessService implements MerchantAccessApplication {

    private final MerchantRepository merchantRepository;
    private final MerchantMembershipRepository membershipRepository;

    @Override
    public MerchantAccessContext authorize(Long merchantId, String actorId, MerchantPermission permission) {
        if (merchantId == null || merchantId <= 0 || actorId == null || actorId.isBlank()
                || actorId.length() > 120 || !actorId.equals(actorId.trim()) || permission == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        MerchantMembership membership = membershipRepository
                .findByMerchantIdAndActorIdAndActiveTrue(merchantId, actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        if (!membership.getRole().permissions().contains(permission)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (merchantRepository.findById(merchantId).filter(merchant -> merchant.isActive()).isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return new MerchantAccessContext(merchantId, actorId, membership.getRole().permissions());
    }
}
