package com.meridian.confirmiq.trade;

import com.meridian.confirmiq.audit.AuditController.AuditView;
import com.meridian.confirmiq.confirmation.ConfirmationStatus;
import com.meridian.confirmiq.confirmation.ConfirmationWorkflowService.AllowedAction;
import com.meridian.confirmiq.counterparty.Counterparty.DeliveryChannel;
import com.meridian.confirmiq.counterparty.CounterpartyDtos.SsiView;
import com.meridian.confirmiq.document.DocumentView;
import com.meridian.confirmiq.inbound.MatchingEngine.FieldResult;
import com.meridian.confirmiq.outbox.OutboxController.OutboxView;
import com.meridian.confirmiq.product.TermField;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Full trade view for the confirmation workspace. */
public record TradeDetail(
        Long id, String tradeRef, String externalRef, String sourceSystem, Trade.IngestionChannel ingestionChannel,
        ProductType productType, String productName, LocalDate tradeDate, Instant executionTime,
        LocalDate valueDate, String currencyPair, String buyCurrency, BigDecimal buyAmount, String sellCurrency,
        BigDecimal sellAmount, BigDecimal rate, LocalDate farValueDate, BigDecimal farBuyAmount,
        BigDecimal farSellAmount, BigDecimal farRate, String settlementCurrency, LocalDate fixingDate,
        String fixingSource, String book, String trader, int economicVersion, Instant createdAt,
        CounterpartyInfo counterparty, ConfirmationInfo confirmation, List<TermField> terms,
        List<SsiPair> settlement, List<DocumentView> documents, List<MatchView> matches,
        List<AuditView> timeline, List<OutboxView> messages, List<AllowedAction> allowedActions,
        List<String> enrichmentIssues) {

    public record CounterpartyInfo(Long id, String shortCode, String legalName, String lei,
                                   DeliveryChannel deliveryChannel, int confirmationSlaHours, String masterAgreement) {}

    public record ConfirmationInfo(ConfirmationStatus status, String statusDescription, String preparedBy,
                                   String approvedBy, Instant sentAt, Instant slaDueAt, boolean slaBreached,
                                   int chaserCount, Instant lastChasedAt, boolean escalated, String disputeReason,
                                   Instant matchedAt, Instant confirmedAt, Instant statusChangedAt) {}

    public record SsiPair(String currency, SsiView house, SsiView counterparty) {}

    public record MatchView(Long id, Long documentId, String outcome, String identifiedBy, boolean qrVerified,
                            Instant createdAt, List<FieldResult> fields) {}
}
