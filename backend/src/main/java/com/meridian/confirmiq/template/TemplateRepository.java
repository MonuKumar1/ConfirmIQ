package com.meridian.confirmiq.template;

import com.meridian.confirmiq.trade.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TemplateRepository extends JpaRepository<ConfirmationTemplate, Long> {

    Optional<ConfirmationTemplate> findFirstByProductTypeAndCounterpartyId(ProductType productType, Long counterpartyId);

    Optional<ConfirmationTemplate> findFirstByProductTypeAndCounterpartyIsNull(ProductType productType);

    boolean existsByCode(String code);
}
