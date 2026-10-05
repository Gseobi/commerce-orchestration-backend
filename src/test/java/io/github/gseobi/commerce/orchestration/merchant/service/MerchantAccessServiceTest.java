package io.github.gseobi.commerce.orchestration.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantPermission;
import io.github.gseobi.commerce.orchestration.merchant.entity.Merchant;
import io.github.gseobi.commerce.orchestration.merchant.entity.MerchantMembership;
import io.github.gseobi.commerce.orchestration.merchant.entity.MembershipRole;
import io.github.gseobi.commerce.orchestration.merchant.repository.MerchantMembershipRepository;
import io.github.gseobi.commerce.orchestration.merchant.repository.MerchantRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantAccessServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private MerchantMembershipRepository membershipRepository;

    @InjectMocks
    private MerchantAccessService service;

    @ParameterizedTest
    @EnumSource(MerchantPermission.class)
    void operatorCanReadAndWrite(MerchantPermission permission) {
        givenMembership(MembershipRole.OPERATOR);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(activeMerchant()));

        var context = service.authorize(1L, "actor-1", permission);

        assertThat(context.merchantId()).isEqualTo(1L);
        assertThat(context.actorId()).isEqualTo("actor-1");
        assertThat(context.permissions()).containsExactlyInAnyOrder(MerchantPermission.values());
        assertThatThrownBy(() -> context.permissions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void viewerCanRead() {
        givenMembership(MembershipRole.VIEWER);
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(activeMerchant()));

        assertThat(service.authorize(1L, "actor-1", MerchantPermission.READ).permissions())
                .containsExactly(MerchantPermission.READ);
    }

    @Test
    void viewerCannotWrite() {
        givenMembership(MembershipRole.VIEWER);
        assertForbidden(1L, "actor-1", MerchantPermission.WRITE);
        verifyNoInteractions(merchantRepository);
    }

    @Test
    void missingMembershipIsForbidden() {
        when(membershipRepository.findByMerchantIdAndActorIdAndActiveTrue(1L, "actor-1"))
                .thenReturn(Optional.empty());
        assertForbidden(1L, "actor-1", MerchantPermission.READ);
        verifyNoInteractions(merchantRepository);
    }

    @Test
    void suspendedMerchantIsForbidden() {
        givenMembership(MembershipRole.OPERATOR);
        Merchant merchant = activeMerchant();
        merchant.suspend();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        assertForbidden(1L, "actor-1", MerchantPermission.READ);
    }

    @Test
    void missingMerchantIsForbidden() {
        givenMembership(MembershipRole.OPERATOR);
        when(merchantRepository.findById(1L)).thenReturn(Optional.empty());
        assertForbidden(1L, "actor-1", MerchantPermission.READ);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", " actor-1", "actor-1 "})
    void invalidIdentityIsForbiddenBeforeQuery(String actorId) {
        assertForbidden(1L, actorId, MerchantPermission.READ);
        verifyNoInteractions(merchantRepository, membershipRepository);
    }

    @Test
    void invalidMerchantAndPermissionAreForbiddenBeforeQuery() {
        assertForbidden(null, "actor-1", MerchantPermission.READ);
        assertForbidden(0L, "actor-1", MerchantPermission.READ);
        assertForbidden(-1L, "actor-1", MerchantPermission.READ);
        assertForbidden(1L, "actor-1", null);
        verifyNoInteractions(merchantRepository, membershipRepository);
    }

    private void assertForbidden(Long merchantId, String actorId, MerchantPermission permission) {
        assertThatThrownBy(() -> service.authorize(merchantId, actorId, permission))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    private void givenMembership(MembershipRole role) {
        when(membershipRepository.findByMerchantIdAndActorIdAndActiveTrue(1L, "actor-1"))
                .thenReturn(Optional.of(new MerchantMembership(1L, "actor-1", role)));
    }

    private Merchant activeMerchant() {
        return new Merchant("SHOP", "Shop", "Asia/Seoul");
    }
}
