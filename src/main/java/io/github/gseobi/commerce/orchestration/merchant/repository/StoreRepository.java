package io.github.gseobi.commerce.orchestration.merchant.repository;

import io.github.gseobi.commerce.orchestration.merchant.entity.Store;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

    boolean existsByMerchantIdAndCode(Long merchantId, String code);

    Optional<Store> findByIdAndMerchantId(Long id, Long merchantId);

    List<Store> findAllByMerchantIdOrderByIdAsc(Long merchantId);
}
