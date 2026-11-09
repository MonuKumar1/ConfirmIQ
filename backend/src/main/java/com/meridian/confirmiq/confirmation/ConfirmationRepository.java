package com.meridian.confirmiq.confirmation;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConfirmationRepository extends JpaRepository<Confirmation, Long>, JpaSpecificationExecutor<Confirmation> {

    @EntityGraph(attributePaths = {"trade", "trade.counterparty"})
    Optional<Confirmation> findByTradeId(Long tradeId);

    @EntityGraph(attributePaths = {"trade", "trade.counterparty"})
    List<Confirmation> findByStatus(ConfirmationStatus status);

    @EntityGraph(attributePaths = {"trade", "trade.counterparty"})
    @Query("select c from Confirmation c")
    List<Confirmation> findAllWithTrade();

    @Query("""
            select c.trade.counterparty.id, count(c) from Confirmation c
            where c.status not in :statuses group by c.trade.counterparty.id""")
    List<Object[]> countByCounterpartyAndStatusNotIn(Collection<ConfirmationStatus> statuses);

    @Query("""
            select c.trade.counterparty.id, count(c) from Confirmation c
            where c.status in :statuses group by c.trade.counterparty.id""")
    List<Object[]> countByCounterpartyAndStatusIn(Collection<ConfirmationStatus> statuses);

    @Query("select c.status, count(c) from Confirmation c group by c.status")
    List<Object[]> countByStatus();
}
