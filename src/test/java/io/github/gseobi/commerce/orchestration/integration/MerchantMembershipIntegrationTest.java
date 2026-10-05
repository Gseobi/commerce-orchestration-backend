package io.github.gseobi.commerce.orchestration.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantAccessApplication;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantApplication;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantPermission;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantRegistrationCommand;
import io.github.gseobi.commerce.orchestration.merchant.entity.MerchantMembership;
import io.github.gseobi.commerce.orchestration.merchant.entity.MembershipRole;
import io.github.gseobi.commerce.orchestration.merchant.repository.MerchantMembershipRepository;
import io.github.gseobi.commerce.orchestration.merchant.repository.MerchantRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("integration-test")
@Tag("integration")
class MerchantMembershipIntegrationTest extends TestcontainersIntegrationSupport {

    @Autowired
    private MerchantApplication merchants;

    @Autowired
    private MerchantAccessApplication access;

    @Autowired
    private MerchantMembershipRepository memberships;

    @Autowired
    private MerchantRepository merchantRepository;

    @Test
    void sameActorHasSeparatePermissionsInEachMerchant() {
        Long first = register("FIRST");
        Long second = register("SECOND");
        Long third = register("THIRD");
        memberships.saveAndFlush(new MerchantMembership(first, "actor-1", MembershipRole.OPERATOR));
        memberships.saveAndFlush(new MerchantMembership(second, "actor-1", MembershipRole.VIEWER));

        assertThat(access.authorize(first, "actor-1", MerchantPermission.WRITE).merchantId()).isEqualTo(first);
        assertThat(access.authorize(second, "actor-1", MerchantPermission.READ).permissions())
                .containsExactly(MerchantPermission.READ);
        assertForbidden(second, MerchantPermission.WRITE);
        assertForbidden(third, MerchantPermission.READ);
    }

    @Test
    void revokedMembershipDeniesNextAuthorization() {
        Long merchantId = register("SHOP");
        MerchantMembership membership = memberships.saveAndFlush(
                new MerchantMembership(merchantId, "actor-1", MembershipRole.OPERATOR));
        access.authorize(merchantId, "actor-1", MerchantPermission.WRITE);

        membership.revoke();
        memberships.saveAndFlush(membership);

        assertForbidden(merchantId, MerchantPermission.WRITE);
    }

    @Test
    void suspendedMerchantDeniesActiveMember() {
        Long merchantId = register("SHOP");
        memberships.saveAndFlush(new MerchantMembership(merchantId, "actor-1", MembershipRole.OPERATOR));
        var merchant = merchantRepository.findById(merchantId).orElseThrow();
        merchant.suspend();
        merchantRepository.saveAndFlush(merchant);

        assertForbidden(merchantId, MerchantPermission.READ);
    }

    @Test
    void databaseRejectsDuplicateMembershipAndUnknownMerchant() {
        Long merchantId = register("SHOP");
        memberships.saveAndFlush(new MerchantMembership(merchantId, "actor-1", MembershipRole.VIEWER));

        assertThatThrownBy(() -> memberships.saveAndFlush(
                new MerchantMembership(merchantId, "actor-1", MembershipRole.OPERATOR)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> memberships.saveAndFlush(
                new MerchantMembership(Long.MAX_VALUE, "actor-2", MembershipRole.VIEWER)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(access.authorize(merchantId, "actor-1", MerchantPermission.READ).permissions())
                .containsExactly(MerchantPermission.READ);
    }

    private Long register(String code) {
        return merchants.registerMerchant(new MerchantRegistrationCommand(code, code, "Asia/Seoul")).id();
    }

    private void assertForbidden(Long merchantId, MerchantPermission permission) {
        assertThatThrownBy(() -> access.authorize(merchantId, "actor-1", permission))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }
}
