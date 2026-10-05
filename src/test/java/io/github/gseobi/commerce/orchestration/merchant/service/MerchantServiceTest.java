package io.github.gseobi.commerce.orchestration.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantRegistrationCommand;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantView;
import io.github.gseobi.commerce.orchestration.merchant.api.StoreRegistrationCommand;
import io.github.gseobi.commerce.orchestration.merchant.api.StoreView;
import io.github.gseobi.commerce.orchestration.merchant.entity.Merchant;
import io.github.gseobi.commerce.orchestration.merchant.entity.Store;
import io.github.gseobi.commerce.orchestration.merchant.repository.MerchantRepository;
import io.github.gseobi.commerce.orchestration.merchant.repository.StoreRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private StoreRepository storeRepository;

    @InjectMocks
    private MerchantService merchantService;

    @Test
    void registerMerchant_normalizesCodeAndReturnsView() {
        when(merchantRepository.existsByCode("SMALL-SHOP")).thenReturn(false);
        when(merchantRepository.saveAndFlush(any(Merchant.class))).thenAnswer(invocation -> {
            Merchant merchant = invocation.getArgument(0);
            ReflectionTestUtils.setField(merchant, "id", 1L);
            return merchant;
        });

        MerchantView result = merchantService.registerMerchant(
                new MerchantRegistrationCommand("small-shop", "작은 상점", "Asia/Seoul")
        );

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.code()).isEqualTo("SMALL-SHOP");
        assertThat(result.defaultRecoveryPolicy()).isEqualTo("MANUAL_REVIEW");
    }

    @Test
    void registerMerchant_rejectsDuplicateCodeBeforeSave() {
        when(merchantRepository.existsByCode("SHOP")).thenReturn(true);

        assertThatThrownBy(() -> merchantService.registerMerchant(
                new MerchantRegistrationCommand("shop", "상점", "Asia/Seoul")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_MERCHANT_CODE);

        verify(merchantRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerMerchant_mapsUniqueConstraintRaceToBusinessError() {
        when(merchantRepository.existsByCode("SHOP")).thenReturn(false);
        when(merchantRepository.saveAndFlush(any(Merchant.class)))
                .thenThrow(new DataIntegrityViolationException("uk_merchants_code"));

        assertThatThrownBy(() -> merchantService.registerMerchant(
                new MerchantRegistrationCommand("shop", "상점", "Asia/Seoul")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_MERCHANT_CODE);
    }

    @Test
    void registerStore_copiesMerchantOperationalDefaults() {
        Merchant merchant = persistedMerchant(1L, "SHOP");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(storeRepository.existsByMerchantIdAndCode(1L, "ONLINE")).thenReturn(false);
        when(storeRepository.saveAndFlush(any(Store.class))).thenAnswer(invocation -> {
            Store store = invocation.getArgument(0);
            ReflectionTestUtils.setField(store, "id", 10L);
            return store;
        });

        StoreView result = merchantService.registerStore(
                new StoreRegistrationCommand(1L, "online", "온라인 스토어")
        );

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.merchantId()).isEqualTo(1L);
        assertThat(result.code()).isEqualTo("ONLINE");
        assertThat(result.timezone()).isEqualTo("Asia/Seoul");
        assertThat(result.recoveryPolicy()).isEqualTo("MANUAL_REVIEW");
    }

    @Test
    void registerStore_mapsUniqueConstraintRaceToBusinessError() {
        Merchant merchant = persistedMerchant(1L, "SHOP");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(storeRepository.existsByMerchantIdAndCode(1L, "ONLINE")).thenReturn(false);
        when(storeRepository.saveAndFlush(any(Store.class)))
                .thenThrow(new DataIntegrityViolationException("uk_stores_merchant_code"));

        assertThatThrownBy(() -> merchantService.registerStore(
                new StoreRegistrationCommand(1L, "online", "온라인 스토어")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_STORE_CODE);
    }

    @Test
    void getStore_usesMerchantScopedRepositoryQuery() {
        when(storeRepository.findByIdAndMerchantId(10L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantService.getStore(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STORE_NOT_FOUND);

        verify(storeRepository).findByIdAndMerchantId(10L, 2L);
        verify(storeRepository, never()).findById(10L);
    }

    @Test
    void getStores_returnsOnlyMerchantScopedRowsInIdOrder() {
        Merchant merchant = persistedMerchant(1L, "SHOP");
        Store first = persistedStore(10L, merchant, "FIRST");
        Store second = persistedStore(11L, merchant, "SECOND");
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(storeRepository.findAllByMerchantIdOrderByIdAsc(1L)).thenReturn(List.of(first, second));

        List<StoreView> result = merchantService.getStores(1L);

        assertThat(result).extracting(StoreView::code).containsExactly("FIRST", "SECOND");
        verify(storeRepository).findAllByMerchantIdOrderByIdAsc(1L);
    }

    private Merchant persistedMerchant(Long id, String code) {
        Merchant merchant = new Merchant(code, "상점", "Asia/Seoul");
        ReflectionTestUtils.setField(merchant, "id", id);
        return merchant;
    }

    private Store persistedStore(Long id, Merchant merchant, String code) {
        Store store = new Store(merchant, code, code + " 스토어");
        ReflectionTestUtils.setField(store, "id", id);
        return store;
    }
}
