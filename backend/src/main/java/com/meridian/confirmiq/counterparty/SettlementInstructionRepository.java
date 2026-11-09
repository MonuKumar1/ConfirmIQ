package com.meridian.confirmiq.counterparty;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SettlementInstructionRepository extends JpaRepository<SettlementInstruction, Long> {

    @Query("select s from SettlementInstruction s where s.counterparty is null and s.active = true order by s.currency")
    List<SettlementInstruction> findHouseInstructions();

    @Query("select s from SettlementInstruction s where s.counterparty is null and s.active = true and s.currency = :ccy")
    Optional<SettlementInstruction> findHouseInstruction(String ccy);

    Optional<SettlementInstruction> findFirstByCounterpartyIdAndCurrencyAndActiveTrueOrderByEffectiveFromDesc(
            Long counterpartyId, String currency);
}
