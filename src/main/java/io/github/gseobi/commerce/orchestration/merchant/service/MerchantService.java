package io.github.gseobi.commerce.orchestration.merchant.service;

import io.github.gseobi.commerce.orchestration.common.error.BusinessException;
import io.github.gseobi.commerce.orchestration.common.error.ErrorCode;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantApplication;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantRegistrationCommand;
import io.github.gseobi.commerce.orchestration.merchant.api.MerchantView;
import io.github.gseobi.commerce.orchestration.merchant.api.StoreRegistrationCommand;
import io.github.gseobi.commerce.orchestration.merchant.api.StoreView;
import io.github.gseobi.commerce.orchestration.merchant.entity.Merchant;
import io.github.gseobi.commerce.orchestration.merchant.entity.Store;
import io.github.gseobi.commerce.orchestration.merchant.repository.MerchantRepository;
import io.github.gseobi.commerce.orchestration.merchant.repository.StoreRepository;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class MerchantService implements MerchantApplication {

    private final MerchantRepository merchantRepository;
    private final StoreRepository storeRepository;

    @Override
    @Transactional
    public MerchantView registerMerchant(MerchantRegistrationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("사업자 등록 요청이 비어 있습니다.");
        }
        String code = normalizeLookupCode(command.code());
        if (merchantRepository.existsByCode(code)) {
            throw new BusinessException(ErrorCode.DUPLICATE_MERCHANT_CODE);
        }
        try {
            return toView(merchantRepository.saveAndFlush(new Merchant(code, command.name(), command.timezone())));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_MERCHANT_CODE);
        }
    }

    @Override
    @Transactional
    public StoreView registerStore(StoreRegistrationCommand command) {
        if (command == null || command.merchantId() == null) {
            throw new IllegalArgumentException("merchantId는 필수입니다.");
        }
        Merchant merchant = getMerchantEntity(command.merchantId());
        String code = normalizeLookupCode(command.code());
        if (storeRepository.existsByMerchantIdAndCode(command.merchantId(), code)) {
            throw new BusinessException(ErrorCode.DUPLICATE_STORE_CODE);
        }
        try {
            return toView(storeRepository.saveAndFlush(new Store(merchant, code, command.name())));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_STORE_CODE);
        }
    }

    @Override
    public MerchantView getMerchant(Long merchantId) {
        return toView(getMerchantEntity(merchantId));
    }

    @Override
    public StoreView getStore(Long merchantId, Long storeId) {
        if (merchantId == null || storeId == null) {
            throw new IllegalArgumentException("merchantId와 storeId는 필수입니다.");
        }
        return storeRepository.findByIdAndMerchantId(storeId, merchantId)
                .map(this::toView)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    @Override
    public List<StoreView> getStores(Long merchantId) {
        getMerchantEntity(merchantId);
        return storeRepository.findAllByMerchantIdOrderByIdAsc(merchantId).stream()
                .map(this::toView)
                .toList();
    }

    private Merchant getMerchantEntity(Long merchantId) {
        if (merchantId == null) {
            throw new IllegalArgumentException("merchantId는 필수입니다.");
        }
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MERCHANT_NOT_FOUND));
    }

    private String normalizeLookupCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("코드는 필수입니다.");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private MerchantView toView(Merchant merchant) {
        return new MerchantView(
                merchant.getId(),
                merchant.getCode(),
                merchant.getName(),
                merchant.getStatus().name(),
                merchant.getTimezone(),
                merchant.getDefaultRecoveryPolicy().name()
        );
    }

    private StoreView toView(Store store) {
        return new StoreView(
                store.getId(),
                store.getMerchantId(),
                store.getCode(),
                store.getName(),
                store.getStatus().name(),
                store.getTimezone(),
                store.getRecoveryPolicy().name()
        );
    }
}
