package com.meridian.confirmiq.trade;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    Optional<Trade> findByTradeRef(String tradeRef);

    boolean existsByExternalRef(String externalRef);

    @Query("""
            select t from Trade t join fetch t.counterparty c
            where lower(t.tradeRef) like lower(concat('%', :q, '%'))
               or lower(t.externalRef) like lower(concat('%', :q, '%'))
               or lower(c.legalName) like lower(concat('%', :q, '%'))
               or lower(c.shortCode) like lower(concat('%', :q, '%'))
            order by t.id desc""")
    List<Trade> search(String q, org.springframework.data.domain.Pageable pageable);
}
