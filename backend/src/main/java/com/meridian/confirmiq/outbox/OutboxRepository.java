package com.meridian.confirmiq.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxMessage, Long>, JpaSpecificationExecutor<OutboxMessage> {

    List<OutboxMessage> findByTradeIdOrderByCreatedAtDesc(Long tradeId);
}
