package io.github.gseobi.commerce.orchestration.merchant.repository;

import io.github.gseobi.commerce.orchestration.merchant.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    boolean existsByCode(String code);
}
