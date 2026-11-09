package com.meridian.confirmiq.inbound;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchResultRepository extends JpaRepository<MatchResult, Long> {

    List<MatchResult> findByTradeIdOrderByCreatedAtDescIdDesc(Long tradeId);

    Optional<MatchResult> findFirstByTradeIdOrderByCreatedAtDescIdDesc(Long tradeId);

    Optional<MatchResult> findFirstByInboundDocumentId(Long documentId);
}
